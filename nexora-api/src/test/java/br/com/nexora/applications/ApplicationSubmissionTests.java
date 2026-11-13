package br.com.nexora.applications;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class ApplicationSubmissionTests {

	private static final String PLATFORM = "{\"resumeType\": \"PLATFORM\"}";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private MockMvc mockMvc;
	private UUID javaSkill;
	private UUID docker;
	private User company;
	private UUID universityId;
	private User universityAccount;
	private String jobId;
	private User student;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		javaSkill = skillId("java");
		docker = skillId("docker");
		company = createCompany("Tech Garanhuns");
		universityAccount = createUser(UserRole.UNIVERSITY);
		universityId = jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'UFAPE', 'APPROVED') RETURNING id",
				UUID.class, universityAccount.getId());
		jobId = publishJob(company, "Estágio Backend Java");
		student = studentWithProfile("Ana Souza");
	}

	@Test
	void validatedStudentAppliesWithTheNexoraResume() throws Exception {
		affiliate(student, "APPROVED");

		as(student, applyTo(jobId, PLATFORM))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.jobId").value(jobId))
				.andExpect(jsonPath("$.jobTitle").value("Estágio Backend Java"))
				.andExpect(jsonPath("$.companyName").value("Tech Garanhuns"))
				.andExpect(jsonPath("$.resumeType").value("PLATFORM"))
				.andExpect(jsonPath("$.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());

		assertEquals(1, applicationCount());
		String snapshot = snapshotOf(student);
		assertEquals("Ana Souza", JsonPath.<String>read(snapshot, "$.profile.fullName"));
		assertEquals("Ciência da Computação", JsonPath.<String>read(snapshot, "$.profile.course"));
		assertEquals("BACKEND", JsonPath.<String>read(snapshot, "$.resume.interestAreas[0]"));
		assertEquals("https://github.com/ana", JsonPath.<String>read(snapshot, "$.resume.links.github"));
		assertEquals("Empresa X", JsonPath.<String>read(snapshot, "$.resume.experiences[0].company"));
		// As competências do catálogo não ficam dentro do Resume: o snapshot as leva à parte.
		assertEquals(List.of(java()), JsonPath.<List<String>>read(snapshot, "$.skills[?(@.name == 'Java')].id"));
		assertEquals(2, JsonPath.<List<Object>>read(snapshot, "$.skills").size());
	}

	@Test
	void laterResumeChangesDoNotAlterTheSentSnapshot() throws Exception {
		affiliate(student, "APPROVED");
		as(student, applyTo(jobId, PLATFORM)).andExpect(status().isCreated());
		String before = snapshotOf(student);

		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Ana Souza Alterada\", \"course\": \"Outro curso\"}"))
				.andExpect(status().isOk());
		as(student, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON)
				.content("{\"interestAreas\": [\"DATA\"]}")).andExpect(status().isOk());
		as(student, put("/api/v1/students/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": []}")).andExpect(status().isOk());

		Object beforeJson = JsonPath.parse(before).json();
		Object afterJson = JsonPath.parse(snapshotOf(student)).json();
		assertEquals(beforeJson, afterJson);
		assertEquals("Ana Souza", JsonPath.<String>read(snapshotOf(student), "$.profile.fullName"));
	}

	@Test
	void snapshotNeverContainsAccountData() throws Exception {
		affiliate(student, "APPROVED");
		as(student, applyTo(jobId, PLATFORM)).andExpect(status().isCreated())
				.andExpect(content().string(not(containsString(student.getEmail()))));

		String snapshot = snapshotOf(student);
		assertFalse(snapshot.contains(student.getEmail()));
		assertFalse(snapshot.contains(student.getId().toString()));
		assertFalse(snapshot.contains("hash"));
	}

	@Test
	void studentWithoutAnApprovedAffiliationCannotApply() throws Exception {
		as(student, applyTo(jobId, PLATFORM))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("STUDENT_NOT_VALIDATED"));
		assertEquals(0, applicationCount());

		for (String affiliationStatus : new String[] { "PENDING", "REJECTED", "REVOKED" }) {
			User other = studentWithProfile("Estudante " + affiliationStatus);
			affiliate(other, affiliationStatus);
			as(other, applyTo(jobId, PLATFORM))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("STUDENT_NOT_VALIDATED"));
		}
		assertEquals(0, applicationCount());
	}

	@Test
	void studentOfASuspendedUniversityCannotApply() throws Exception {
		affiliate(student, "APPROVED");
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", universityAccount.getId());
		entityManager.clear();

		as(student, applyTo(jobId, PLATFORM))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("STUDENT_NOT_VALIDATED"));
		assertEquals(0, applicationCount());
	}

	@Test
	void closedJobDoesNotAcceptApplications() throws Exception {
		affiliate(student, "APPROVED");
		as(company, post("/api/v1/jobs/" + jobId + "/close")).andExpect(status().isOk());

		as(student, applyTo(jobId, PLATFORM))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("JOB_NOT_ACCEPTING_APPLICATIONS"));
		assertEquals(0, applicationCount());
	}

	@Test
	void jobOfASuspendedCompanyDoesNotAcceptApplications() throws Exception {
		affiliate(student, "APPROVED");
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", company.getId());
		entityManager.clear();

		as(student, applyTo(jobId, PLATFORM))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("JOB_NOT_ACCEPTING_APPLICATIONS"));
	}

	@Test
	void deletedOrUnknownJobIsNotFound() throws Exception {
		affiliate(student, "APPROVED");
		as(company, delete("/api/v1/jobs/" + jobId)).andExpect(status().isNoContent());

		as(student, applyTo(jobId, PLATFORM))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(student, applyTo(UUID.randomUUID().toString(), PLATFORM))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
	}

	@Test
	void secondApplicationToTheSameJobIsRejected() throws Exception {
		affiliate(student, "APPROVED");
		as(student, applyTo(jobId, PLATFORM)).andExpect(status().isCreated());

		as(student, applyTo(jobId, PLATFORM))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_EXISTS"));
		assertEquals(1, applicationCount());
	}

	@Test
	void sameStudentMayApplyToOtherJobsAndOtherStudentsToTheSameJob() throws Exception {
		affiliate(student, "APPROVED");
		User colleague = studentWithProfile("Bruno Lima");
		affiliate(colleague, "APPROVED");
		String otherJob = publishJob(company, "Outra vaga");

		as(student, applyTo(jobId, PLATFORM)).andExpect(status().isCreated());
		as(student, applyTo(otherJob, PLATFORM)).andExpect(status().isCreated());
		as(colleague, applyTo(jobId, PLATFORM)).andExpect(status().isCreated());
		assertEquals(3, applicationCount());
	}

	@Test
	void databaseRejectsDuplicateStudentJobPairs() {
		affiliate(student, "APPROVED");
		UUID studentId = studentProfileId(student);
		String insert = "INSERT INTO applications (student_id, job_id, resume_type, status, resume_snapshot) "
				+ "VALUES (?, ?, 'PLATFORM', 'SUBMITTED', '{}'::jsonb)";
		jdbcTemplate.update(insert, studentId, UUID.fromString(jobId));

		assertThrows(DataIntegrityViolationException.class,
				() -> jdbcTemplate.update(insert, studentId, UUID.fromString(jobId)));
	}

	@Test
	void resumeTypeIsRequiredAndOnlyPlatformIsAcceptedHere() throws Exception {
		affiliate(student, "APPROVED");
		as(student, applyTo(jobId, "{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("resumeType"));
		as(student, applyTo(jobId, "{\"resumeType\": \"WORD\"}")).andExpect(status().isBadRequest());
		assertEquals(0, applicationCount());
	}

	@Test
	void studentWithoutProfileCannotApply() throws Exception {
		as(createUser(UserRole.STUDENT), applyTo(jobId, PLATFORM))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_FOUND"));
	}

	@Test
	void onlyStudentsCanApply() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.COMPANY, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			as(createUser(role), applyTo(jobId, PLATFORM)).andExpect(status().isForbidden());
		}
		mockMvc.perform(applyTo(jobId, PLATFORM)).andExpect(status().isUnauthorized());
	}

	@Test
	void suspendedStudentIsBlocked() throws Exception {
		affiliate(student, "APPROVED");
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", student.getId());
		entityManager.clear();

		as(student, applyTo(jobId, PLATFORM))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
	}

	private static MockHttpServletRequestBuilder applyTo(String job, String body) {
		return post("/api/v1/jobs/" + job + "/applications").contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private long applicationCount() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM applications", Long.class);
	}

	private String snapshotOf(User owner) {
		entityManager.flush();
		return jdbcTemplate.queryForObject(
				"SELECT resume_snapshot::text FROM applications WHERE student_id = ?", String.class,
				studentProfileId(owner));
	}

	private String java() {
		return javaSkill.toString();
	}

	private UUID studentProfileId(User owner) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class, owner.getId());
	}

	private void affiliate(User owner, String status) {
		jdbcTemplate.update("INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, ?)",
				studentProfileId(owner), universityId, status);
	}

	private User studentWithProfile(String name) throws Exception {
		User owner = createUser(UserRole.STUDENT);
		as(owner, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON).content("""
				{"fullName": "%s", "course": "Ciência da Computação", "institution": "UFAPE",
				 "location": "Garanhuns, PE", "summary": "Resumo de %s."}
				""".formatted(name, name))).andExpect(status().isOk());
		as(owner, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON).content("""
				{"interestAreas": ["BACKEND"], "links": {"github": "https://github.com/ana"},
				 "experiences": [{"company": "Empresa X", "role": "Estagiária", "startDate": "2025-01"}]}
				""")).andExpect(status().isOk());
		as(owner, put("/api/v1/students/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [\"%s\", \"%s\"]}".formatted(javaSkill, docker))).andExpect(status().isOk());
		return owner;
	}

	private String publishJob(User owner, String title) throws Exception {
		String response = as(owner, post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "%s", "description": "Descrição da vaga.", "area": "BACKEND", "location": null,
				 "workModel": "REMOTE", "level": "INTERNSHIP", "requiredSkillIds": ["%s"], "desiredSkillIds": []}
				""".formatted(title, javaSkill)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private UUID skillId(String normalizedName) {
		return jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = ?", UUID.class,
				normalizedName);
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private User createCompany(String name) throws Exception {
		User owner = createUser(UserRole.COMPANY);
		as(owner, put("/api/v1/companies/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"%s\"}".formatted(name)))
				.andExpect(status().isOk());
		return owner;
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}
}
