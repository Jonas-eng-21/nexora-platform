package br.com.nexora.matching;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class StudentRecommendationTests {

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
	private UUID java;
	private UUID python;
	private UUID docker;
	private User company;
	private User universityAccount;
	private UUID universityId;
	private String jobId;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		java = skillId("java");
		python = skillId("python");
		docker = skillId("docker");
		company = createCompany("Tech Garanhuns");
		universityAccount = createUser(UserRole.UNIVERSITY);
		universityId = jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'UFAPE', 'APPROVED') RETURNING id",
				UUID.class, universityAccount.getId());
		jobId = publishJob(company, "Estágio Backend Java", "BACKEND", "REMOTE", java, docker);
	}

	@Test
	void ownerSeesValidatedStudentsOrderedByScoreWithAuthorizedDataOnly() throws Exception {
		// Criados do menos para o mais aderente, para a ordem do resultado não coincidir com a de criação.
		User weak = validatedStudent("Carla Dias", List.of(python), "DATA");
		User medium = validatedStudent("Bruno Lima", List.of(java), "BACKEND");
		User strong = validatedStudent("Ana Souza", List.of(java, docker), "BACKEND");

		as(company, get(path(jobId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].student.fullName").value("Ana Souza"))
				.andExpect(jsonPath("$.content[0].student.id").value(studentId(strong).toString()))
				.andExpect(jsonPath("$.content[0].student.course").value("Ciência da Computação"))
				.andExpect(jsonPath("$.content[0].student.location").value("Garanhuns, PE"))
				.andExpect(jsonPath("$.content[0].student.summary").value("Resumo de Ana Souza."))
				.andExpect(jsonPath("$.content[0].student.skills[0]").value("Docker"))
				.andExpect(jsonPath("$.content[0].student.skills[1]").value("Java"))
				.andExpect(jsonPath("$.content[0].student.interestAreas[0]").value("BACKEND"))
				.andExpect(jsonPath("$.content[0].match.score").value(100))
				.andExpect(jsonPath("$.content[0].match.matchedRequiredSkills[0]").value("Java"))
				.andExpect(jsonPath("$.content[0].match.summary").isNotEmpty())
				.andExpect(jsonPath("$.content[0].match.criteria.length()").value(4))
				// Obrigatória 45 + desejável 0 + interesse 20 + remoto e sem preferência 15 = 80.
				.andExpect(jsonPath("$.content[1].student.fullName").value("Bruno Lima"))
				.andExpect(jsonPath("$.content[1].student.id").value(studentId(medium).toString()))
				.andExpect(jsonPath("$.content[1].match.score").value(80))
				.andExpect(jsonPath("$.content[1].match.missingDesiredSkills[0]").value("Docker"))
				// Só vaga remota e ausência de preferência: 15 pontos.
				.andExpect(jsonPath("$.content[2].student.fullName").value("Carla Dias"))
				.andExpect(jsonPath("$.content[2].student.id").value(studentId(weak).toString()))
				.andExpect(jsonPath("$.content[2].match.score").value(15));
	}

	@Test
	void responseDoesNotExposePrivateStudentData() throws Exception {
		User student = validatedStudent("Ana Souza", List.of(java), "BACKEND");
		as(student, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON).content("""
				{"interestAreas": ["BACKEND"],
				 "links": {"github": "https://github.com/ana-privado"},
				 "experiences": [{"company": "Empresa Sigilosa", "role": "Estagiária", "startDate": "2025-01"}]}
				""")).andExpect(status().isOk());

		as(company, get(path(jobId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].student.email").doesNotExist())
				.andExpect(jsonPath("$.content[0].student.userId").doesNotExist())
				.andExpect(jsonPath("$.content[0].student.institution").doesNotExist())
				.andExpect(jsonPath("$.content[0].student.resume").doesNotExist())
				.andExpect(jsonPath("$.content[0].student.links").doesNotExist())
				.andExpect(content().string(not(containsString(student.getEmail()))))
				.andExpect(content().string(not(containsString("hash"))))
				.andExpect(content().string(not(containsString("ana-privado"))))
				.andExpect(content().string(not(containsString("Empresa Sigilosa"))));
	}

	@Test
	void onlyAcademicallyValidatedStudentsWithActiveAccountsAreListed() throws Exception {
		User validated = validatedStudent("Validada", List.of(java), "BACKEND");
		createStudentProfile("Sem vínculo", List.of(java), "BACKEND");
		affiliate(createStudentProfile("Vínculo pendente", List.of(java), "BACKEND"), universityId, "PENDING");
		affiliate(createStudentProfile("Vínculo rejeitado", List.of(java), "BACKEND"), universityId, "REJECTED");
		affiliate(createStudentProfile("Vínculo revogado", List.of(java), "BACKEND"), universityId, "REVOKED");
		User suspendedStudent = validatedStudent("Conta suspensa", List.of(java), "BACKEND");
		User otherUniversityAccount = createUser(UserRole.UNIVERSITY);
		UUID suspendedUniversity = jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'Suspensa', 'APPROVED') RETURNING id",
				UUID.class, otherUniversityAccount.getId());
		affiliate(createStudentProfile("Universidade suspensa", List.of(java), "BACKEND"), suspendedUniversity,
				"APPROVED");
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id IN (?, ?)", suspendedStudent.getId(),
				otherUniversityAccount.getId());
		entityManager.clear();

		as(company, get(path(jobId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].student.id").value(studentId(validated).toString()));
	}

	@Test
	void listIsPaginatedAndStudentsWithTheSameScoreKeepAStableOrderNewestFirst() throws Exception {
		validatedStudent("Mais antiga", List.of(java), "BACKEND");
		validatedStudent("Mais nova", List.of(java), "BACKEND");
		validatedStudent("Menos aderente", List.of(python), "DATA");

		for (int attempt = 0; attempt < 3; attempt++) {
			as(company, get(path(jobId)).param("size", "2"))
					.andExpect(jsonPath("$.content.length()").value(2))
					.andExpect(jsonPath("$.page").value(0))
					.andExpect(jsonPath("$.size").value(2))
					.andExpect(jsonPath("$.totalElements").value(3))
					.andExpect(jsonPath("$.totalPages").value(2))
					.andExpect(jsonPath("$.content[0].student.fullName").value("Mais nova"))
					.andExpect(jsonPath("$.content[1].student.fullName").value("Mais antiga"));
		}
		as(company, get(path(jobId)).param("size", "2").param("page", "1"))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].student.fullName").value("Menos aderente"));
		as(company, get(path(jobId)).param("size", "2").param("page", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void emptyListWhenNoStudentIsValidated() throws Exception {
		createStudentProfile("Sem vínculo", List.of(java), "BACKEND");

		as(company, get(path(jobId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void ownerAlsoSeesStudentsOfItsClosedJob() throws Exception {
		validatedStudent("Ana Souza", List.of(java), "BACKEND");
		as(company, post("/api/v1/jobs/" + jobId + "/close")).andExpect(status().isOk());

		as(company, get(path(jobId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void companyCannotListStudentsOfAnotherCompanysDeletedOrUnknownJob() throws Exception {
		validatedStudent("Ana Souza", List.of(java), "BACKEND");
		User intruder = createCompany("Outra Empresa");
		as(intruder, get(path(jobId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));

		as(company, get(path(UUID.randomUUID().toString())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));

		as(company, delete("/api/v1/jobs/" + jobId)).andExpect(status().isNoContent());
		as(company, get(path(jobId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
	}

	@Test
	void companyWithoutProfileGetsNotFound() throws Exception {
		as(createUser(UserRole.COMPANY), get(path(jobId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMPANY_PROFILE_NOT_FOUND"));
	}

	@Test
	void onlyCompaniesCanUseTheCompanyRoute() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			as(createUser(role), get(path(jobId))).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(path(jobId))).andExpect(status().isUnauthorized());
	}

	@Test
	void suspendedCompanyIsBlocked() throws Exception {
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", company.getId());
		entityManager.clear();

		as(company, get(path(jobId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
	}

	@Test
	void adminSeesTheSameListThroughTheAdminRoute() throws Exception {
		validatedStudent("Bruno Lima", List.of(java), "BACKEND");
		validatedStudent("Ana Souza", List.of(java, docker), "BACKEND");
		User admin = createUser(UserRole.ADMIN);

		as(admin, get(adminPath(jobId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].student.fullName").value("Ana Souza"))
				.andExpect(jsonPath("$.content[0].match.score").value(100))
				.andExpect(jsonPath("$.content[1].student.fullName").value("Bruno Lima"))
				.andExpect(jsonPath("$.content[0].student.email").doesNotExist());
	}

	@Test
	void adminRouteRejectsOtherRolesAndHidesDeletedJobs() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.COMPANY, UserRole.UNIVERSITY }) {
			as(createUser(role), get(adminPath(jobId))).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(adminPath(jobId))).andExpect(status().isUnauthorized());

		User admin = createUser(UserRole.ADMIN);
		as(admin, get(adminPath(UUID.randomUUID().toString())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(company, delete("/api/v1/jobs/" + jobId)).andExpect(status().isNoContent());
		as(admin, get(adminPath(jobId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
	}

	private static String path(String id) {
		return "/api/v1/matching/jobs/" + id + "/students";
	}

	private static String adminPath(String id) {
		return "/api/v1/admin/jobs/" + id + "/matching/students";
	}

	private User validatedStudent(String name, List<UUID> skills, String interestArea) throws Exception {
		User student = createStudentProfile(name, skills, interestArea);
		affiliate(student, universityId, "APPROVED");
		return student;
	}

	private User createStudentProfile(String name, List<UUID> skills, String interestArea) throws Exception {
		User student = createUser(UserRole.STUDENT);
		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON).content("""
				{"fullName": "%s", "course": "Ciência da Computação", "institution": "UFAPE",
				 "location": "Garanhuns, PE", "summary": "Resumo de %s."}
				""".formatted(name, name))).andExpect(status().isOk());
		as(student, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON)
				.content("{\"interestAreas\": [\"%s\"]}".formatted(interestArea))).andExpect(status().isOk());
		String ids = String.join(", ", skills.stream().map(id -> "\"" + id + "\"").toList());
		as(student, put("/api/v1/students/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [%s]}".formatted(ids))).andExpect(status().isOk());
		return student;
	}

	private void affiliate(User student, UUID university, String status) {
		jdbcTemplate.update("INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, ?)",
				studentId(student), university, status);
	}

	private UUID studentId(User student) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class, student.getId());
	}

	private String publishJob(User owner, String title, String area, String workModel, UUID requiredSkill,
			UUID desiredSkill) throws Exception {
		String response = as(owner, post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "%s", "description": "Descrição da vaga.", "area": "%s", "location": null,
				 "workModel": "%s", "level": "INTERNSHIP", "requiredSkillIds": ["%s"], "desiredSkillIds": ["%s"]}
				""".formatted(title, area, workModel, requiredSkill, desiredSkill)))
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
