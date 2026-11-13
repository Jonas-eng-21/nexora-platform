package br.com.nexora.applications;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
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
class StudentApplicationListTests {

	private static final String LIST = "/api/v1/applications/me";

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
	private UUID universityId;
	private UUID javaSkill;
	private User company;
	private User student;
	private final Instant base = Instant.parse("2026-11-01T12:00:00Z");

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		javaSkill = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = 'java'", UUID.class);
		company = createCompany("Tech Garanhuns");
		User universityAccount = createUser(UserRole.UNIVERSITY);
		universityId = jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'UFAPE', 'APPROVED') RETURNING id",
				UUID.class, universityAccount.getId());
		student = validatedStudent("Ana Souza");
	}

	@Test
	void studentSeesOnlyOwnApplicationsWithTheDecidedFields() throws Exception {
		String job = publishJob(company, "Estágio Backend Java");
		UUID mine = insert(student, job, "IN_REVIEW", "PLATFORM", base);
		User other = validatedStudent("Bruno Lima");
		insert(other, publishJob(company, "Vaga do Bruno"), "SUBMITTED", "PLATFORM", base);

		as(student, get(LIST))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(mine.toString()))
				.andExpect(jsonPath("$.content[0].jobId").value(job))
				.andExpect(jsonPath("$.content[0].jobTitle").value("Estágio Backend Java"))
				.andExpect(jsonPath("$.content[0].companyName").value("Tech Garanhuns"))
				.andExpect(jsonPath("$.content[0].status").value("IN_REVIEW"))
				.andExpect(jsonPath("$.content[0].resumeType").value("PLATFORM"))
				.andExpect(jsonPath("$.content[0].appliedAt").value("2026-11-01T12:00:00Z"))
				.andExpect(jsonPath("$.content[0].updatedAt").isNotEmpty())
				.andExpect(jsonPath("$.content[0].length()").value(8));
	}

	@Test
	void studentIdSentByTheClientIsNeverUsed() throws Exception {
		User other = validatedStudent("Bruno Lima");
		insert(other, publishJob(company, "Vaga do Bruno"), "SUBMITTED", "PLATFORM", base);

		as(student, get(LIST).param("studentId", studentProfileId(other).toString()).param("userId",
				other.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void applicationsAreOrderedByAppliedAtThenIdDescending() throws Exception {
		List<UUID> ids = new ArrayList<>();
		ids.add(insert(student, publishJob(company, "Mais antiga"), "SUBMITTED", "PLATFORM", base));
		ids.add(insert(student, publishJob(company, "Empate 1"), "SUBMITTED", "PLATFORM", base.plus(2, ChronoUnit.DAYS)));
		ids.add(insert(student, publishJob(company, "Empate 2"), "SUBMITTED", "PLATFORM", base.plus(2, ChronoUnit.DAYS)));
		ids.add(insert(student, publishJob(company, "Mais nova"), "SUBMITTED", "PLATFORM", base.plus(5, ChronoUnit.DAYS)));
		entityManager.flush();
		// A ordem de desempate é a do banco para uuid (bytes), não a de UUID.compareTo do Java.
		List<String> expected = jdbcTemplate.queryForList(
				"SELECT id::text FROM applications WHERE student_id = ? ORDER BY created_at DESC, id DESC",
				String.class, studentProfileId(student));

		for (int attempt = 0; attempt < 3; attempt++) {
			String body = as(student, get(LIST)).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			List<String> actual = JsonPath.read(body, "$.content[*].id");
			assertEquals(expected, actual);
		}
		as(student, get(LIST))
				.andExpect(jsonPath("$.content[0].jobTitle").value("Mais nova"))
				.andExpect(jsonPath("$.content[3].jobTitle").value("Mais antiga"));
		assertEquals(4, ids.size());
	}

	@Test
	void paginationUsesTheDecidedDefaultsAndLimits() throws Exception {
		for (int i = 0; i < 12; i++) {
			insert(student, publishJob(company, "Vaga " + i), "SUBMITTED", "PLATFORM", base.plus(i, ChronoUnit.HOURS));
		}

		as(student, get(LIST))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.content.length()").value(10))
				.andExpect(jsonPath("$.totalElements").value(12))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content[0].jobTitle").value("Vaga 11"));
		as(student, get(LIST).param("page", "1"))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[1].jobTitle").value("Vaga 0"));
		as(student, get(LIST).param("page", "9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(12));
		// Valores fora do intervalo são ajustados, como nas demais listas: máximo 50, mínimo 1, página mínima 0.
		as(student, get(LIST).param("size", "500")).andExpect(jsonPath("$.size").value(50))
				.andExpect(jsonPath("$.content.length()").value(12));
		as(student, get(LIST).param("size", "0")).andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.content.length()").value(1));
		as(student, get(LIST).param("page", "-3")).andExpect(jsonPath("$.page").value(0));
	}

	@Test
	void statusFilterWorksAndEveryStatusIsVisibleWithoutIt() throws Exception {
		String[] statuses = { "SUBMITTED", "IN_REVIEW", "APPROVED", "REJECTED", "SUSPENDED" };
		for (int i = 0; i < statuses.length; i++) {
			insert(student, publishJob(company, "Vaga " + statuses[i]), statuses[i], "PLATFORM",
					base.plus(i, ChronoUnit.HOURS));
		}

		as(student, get(LIST))
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.content[?(@.status == 'SUSPENDED')].jobTitle").value("Vaga SUSPENDED"))
				.andExpect(jsonPath("$.content[?(@.status == 'REJECTED')].jobTitle").value("Vaga REJECTED"))
				.andExpect(jsonPath("$.content[?(@.status == 'APPROVED')].jobTitle").value("Vaga APPROVED"));
		for (String wanted : statuses) {
			as(student, get(LIST).param("status", wanted))
					.andExpect(jsonPath("$.totalElements").value(1))
					.andExpect(jsonPath("$.content[0].status").value(wanted));
		}
		as(student, get(LIST).param("status", "ARCHIVED")).andExpect(status().isBadRequest());
	}

	@Test
	void statusFilterAndPaginationWorkTogether() throws Exception {
		for (int i = 0; i < 3; i++) {
			insert(student, publishJob(company, "Suspensa " + i), "SUSPENDED", "PLATFORM", base.plus(i, ChronoUnit.HOURS));
		}
		insert(student, publishJob(company, "Enviada"), "SUBMITTED", "PLATFORM", base.plus(9, ChronoUnit.HOURS));

		as(student, get(LIST).param("status", "SUSPENDED").param("size", "2"))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].jobTitle").value("Suspensa 2"));
	}

	@Test
	void listingNeverCarriesTheResumeContent() throws Exception {
		String platformJob = publishJob(company, "Com snapshot");
		as(student, multipart("/api/v1/jobs/" + platformJob + "/applications").param("resumeType", "PLATFORM"))
				.andExpect(status().isCreated());
		insert(student, publishJob(company, "Com PDF"), "SUBMITTED", "PDF", base);

		as(student, get(LIST))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[?(@.resumeType == 'PDF')].jobTitle").value("Com PDF"))
				.andExpect(jsonPath("$.content[0].resumeSnapshot").doesNotExist())
				.andExpect(jsonPath("$.content[0].snapshot").doesNotExist())
				.andExpect(jsonPath("$.content[0].storageKey").doesNotExist())
				.andExpect(content().string(not(containsString("interestAreas"))))
				.andExpect(content().string(not(containsString("fullName"))))
				.andExpect(content().string(not(containsString(".pdf"))))
				.andExpect(content().string(not(containsString(student.getEmail()))));
	}

	@Test
	void studentWithoutProfileGetsAnEmptyPage() throws Exception {
		as(createUser(UserRole.STUDENT), get(LIST))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void onlyAuthenticatedStudentsCanList() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.COMPANY, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			as(createUser(role), get(LIST)).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(LIST)).andExpect(status().isUnauthorized());

		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", student.getId());
		entityManager.clear();
		as(student, get(LIST))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
	}

	@Test
	void listingLoadsJobsAndCompaniesWithoutOneQueryPerRow() throws Exception {
		Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		insert(student, publishJob(company, "Primeira"), "SUBMITTED", "PLATFORM", base);
		long few = statementsForList(statistics);

		for (int i = 0; i < 5; i++) {
			User otherCompany = createCompany("Empresa " + i);
			insert(student, publishJob(otherCompany, "Vaga " + i), "SUBMITTED", "PLATFORM",
					base.plus(i + 1, ChronoUnit.HOURS));
		}
		long many = statementsForList(statistics);

		assertEquals(few, many);
		// Nada é carregado por id depois da consulta principal: vaga e empresa vêm junto com a candidatura.
		assertEquals(0, statistics.getEntityFetchCount());
	}

	@Test
	void studentReadsTheSentSnapshotLaterAndItNeverChanges() throws Exception {
		String job = publishJob(company, "Estágio Backend Java");
		String created = as(student, multipart("/api/v1/jobs/" + job + "/applications").param("resumeType", "PLATFORM"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.id");
		String path = "/api/v1/applications/" + id + "/resume";

		String first = as(student, get(path))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.profile.fullName").value("Ana Souza"))
				.andExpect(jsonPath("$.resume.interestAreas[0]").value("BACKEND"))
				.andExpect(jsonPath("$.skills[0].name").value("Java"))
				.andExpect(content().string(not(containsString(student.getEmail()))))
				.andReturn().getResponse().getContentAsString();

		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Ana Alterada\"}")).andExpect(status().isOk());
		as(student, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON)
				.content("{\"interestAreas\": [\"DATA\"]}")).andExpect(status().isOk());

		String second = as(student, get(path)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		Object firstJson = JsonPath.parse(first).json();
		Object secondJson = JsonPath.parse(second).json();
		assertEquals(firstJson, secondJson);
		// A candidatura não tem rota de edição: o currículo enviado só pode ser lido.
		as(student, put(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isMethodNotAllowed());
		// A empresa dona e o admin leem o mesmo documento; quem não é parte da candidatura não acha nada.
		as(company, get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.profile.fullName").value("Ana Souza"));
		as(createUser(UserRole.ADMIN), get(path)).andExpect(status().isOk());
		as(validatedStudent("Bruno Lima"), get(path))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		as(createCompany("Outra Empresa"), get(path)).andExpect(status().isNotFound());
		assertTrue(second.contains("Ana Souza") && !second.contains("Ana Alterada"));
	}

	private long statementsForList(Statistics statistics) throws Exception {
		entityManager.flush();
		entityManager.clear();
		statistics.clear();
		as(student, get(LIST)).andExpect(status().isOk());
		return statistics.getPrepareStatementCount();
	}

	/** Insere direto no banco para controlar status e data; o envio pela API é coberto nos testes de candidatura. */
	private UUID insert(User owner, String job, String status, String resumeType, Instant createdAt) {
		UUID studentId = studentProfileId(owner);
		Timestamp when = Timestamp.from(createdAt);
		if (resumeType.equals("PDF")) {
			return jdbcTemplate.queryForObject("""
					INSERT INTO applications (student_id, job_id, resume_type, status, storage_key, original_filename,
					                          file_size, content_type, created_at, updated_at,
					                          suspended_from_status, suspension_reason, suspended_at)
					VALUES (?, ?, 'PDF', ?, ?, 'curriculo.pdf', 10, 'application/pdf', ?, ?,
					        CASE WHEN ?::text = 'SUSPENDED' THEN 'SUBMITTED' END,
					        CASE WHEN ?::text = 'SUSPENDED' THEN 'ACADEMIC_AFFILIATION_REVOKED' END,
					        CASE WHEN ?::text = 'SUSPENDED' THEN ?::timestamptz END) RETURNING id
					""", UUID.class, studentId, UUID.fromString(job), status, UUID.randomUUID() + ".pdf", when, when,
					status, status, status, when);
		}
		return jdbcTemplate.queryForObject("""
				INSERT INTO applications (student_id, job_id, resume_type, status, resume_snapshot, created_at, updated_at,
				                          suspended_from_status, suspension_reason, suspended_at)
				VALUES (?, ?, 'PLATFORM', ?, '{}'::jsonb, ?, ?,
				        CASE WHEN ?::text = 'SUSPENDED' THEN 'SUBMITTED' END,
				        CASE WHEN ?::text = 'SUSPENDED' THEN 'ACADEMIC_AFFILIATION_REVOKED' END,
				        CASE WHEN ?::text = 'SUSPENDED' THEN ?::timestamptz END) RETURNING id
				""", UUID.class, studentId, UUID.fromString(job), status, when, when, status, status, status, when);
	}

	private UUID studentProfileId(User owner) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class, owner.getId());
	}

	private User validatedStudent(String name) throws Exception {
		User owner = createUser(UserRole.STUDENT);
		as(owner, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"%s\"}".formatted(name))).andExpect(status().isOk());
		as(owner, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON)
				.content("{\"interestAreas\": [\"BACKEND\"]}")).andExpect(status().isOk());
		as(owner, put("/api/v1/students/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [\"%s\"]}".formatted(javaSkill))).andExpect(status().isOk());
		jdbcTemplate.update("INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, 'APPROVED')",
				studentProfileId(owner), universityId);
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

	private ResultActions as(User user, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}
}
