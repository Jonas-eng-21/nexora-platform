package br.com.nexora.applications;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class JobApplicationListTests {

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
	private UUID docker;
	private UUID python;
	private User company;
	private String jobId;
	private final Instant base = Instant.parse("2026-11-01T12:00:00Z");

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		java = skillId("java");
		docker = skillId("docker");
		python = skillId("python");
		company = createCompany("Tech Garanhuns");
		jobId = publishJob(company, "Estágio Backend Java");
	}

	@Test
	void ownerListsTheApplicationsOfItsJobWithTheDecidedFields() throws Exception {
		User ana = student("Ana Souza", List.of(java, docker), "BACKEND");
		UUID id = insert(ana, jobId, "IN_REVIEW", "PLATFORM", base);

		as(company, get(path(jobId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(id.toString()))
				.andExpect(jsonPath("$.content[0].status").value("IN_REVIEW"))
				.andExpect(jsonPath("$.content[0].resumeType").value("PLATFORM"))
				.andExpect(jsonPath("$.content[0].appliedAt").value("2026-11-01T12:00:00Z"))
				.andExpect(jsonPath("$.content[0].updatedAt").isNotEmpty())
				.andExpect(jsonPath("$.content[0].matchScore").value(100))
				.andExpect(jsonPath("$.content[0].student.id").value(studentProfileId(ana).toString()))
				.andExpect(jsonPath("$.content[0].student.fullName").value("Ana Souza"))
				.andExpect(jsonPath("$.content[0].student.course").value("Ciência da Computação"))
				.andExpect(jsonPath("$.content[0].student.location").value("Garanhuns, PE"))
				.andExpect(jsonPath("$.content[0].student.summary").value("Resumo de Ana Souza."))
				.andExpect(jsonPath("$.content[0].student.skills[0]").value("Docker"))
				.andExpect(jsonPath("$.content[0].student.skills[1]").value("Java"))
				.andExpect(jsonPath("$.content[0].student.interestAreas[0]").value("BACKEND"))
				.andExpect(jsonPath("$.content[0].length()").value(7))
				.andExpect(jsonPath("$.content[0].student.length()").value(7));
	}

	@Test
	void onlyApplicationsOfThatJobAreListed() throws Exception {
		User ana = student("Ana Souza", List.of(java), "BACKEND");
		User bruno = student("Bruno Lima", List.of(java), "BACKEND");
		String otherJob = publishJob(company, "Outra vaga");
		UUID mine = insert(ana, jobId, "SUBMITTED", "PLATFORM", base);
		insert(bruno, otherJob, "SUBMITTED", "PLATFORM", base);

		as(company, get(path(jobId)))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(mine.toString()));
		as(company, post("/api/v1/jobs/" + jobId + "/close")).andExpect(status().isOk());
		// Vaga encerrada continua consultável pela dona: o histórico não some.
		as(company, get(path(jobId))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void studentSummaryNeverExposesPrivateDataAndTheListNeverCarriesTheResume() throws Exception {
		User ana = student("Ana Souza", List.of(java), "BACKEND");
		as(ana, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON).content("""
				{"interestAreas": ["BACKEND"], "links": {"github": "https://github.com/ana-privado"},
				 "experiences": [{"company": "Empresa Sigilosa", "role": "Estagiária", "startDate": "2025-01"}]}
				""")).andExpect(status().isOk());
		insert(ana, jobId, "SUBMITTED", "PLATFORM", base);
		User bruno = student("Bruno Lima", List.of(java), "BACKEND");
		insert(bruno, jobId, "SUBMITTED", "PDF", base.plusSeconds(60));

		as(company, get(path(jobId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].student.email").doesNotExist())
				.andExpect(jsonPath("$.content[0].student.userId").doesNotExist())
				.andExpect(jsonPath("$.content[0].student.institution").doesNotExist())
				.andExpect(jsonPath("$.content[0].student.links").doesNotExist())
				.andExpect(jsonPath("$.content[0].resumeSnapshot").doesNotExist())
				.andExpect(jsonPath("$.content[0].snapshot").doesNotExist())
				.andExpect(jsonPath("$.content[0].storageKey").doesNotExist())
				.andExpect(jsonPath("$.content[0].resumeFileName").doesNotExist())
				.andExpect(content().string(not(containsString(ana.getEmail()))))
				.andExpect(content().string(not(containsString(bruno.getEmail()))))
				.andExpect(content().string(not(containsString("ana-privado"))))
				.andExpect(content().string(not(containsString("Empresa Sigilosa"))))
				.andExpect(content().string(not(containsString(".pdf"))))
				.andExpect(content().string(not(containsString("hash"))));
	}

	@Test
	void applicationsAreOrderedByAppliedAtThenIdDescending() throws Exception {
		insert(student("Mais antiga", List.of(java), "BACKEND"), jobId, "SUBMITTED", "PLATFORM", base);
		Instant tie = base.plus(2, ChronoUnit.DAYS);
		insert(student("Empate 1", List.of(java), "BACKEND"), jobId, "SUBMITTED", "PLATFORM", tie);
		insert(student("Empate 2", List.of(java), "BACKEND"), jobId, "SUBMITTED", "PLATFORM", tie);
		insert(student("Mais nova", List.of(java), "BACKEND"), jobId, "SUBMITTED", "PLATFORM",
				base.plus(5, ChronoUnit.DAYS));
		entityManager.flush();
		// Desempate pela ordem do banco para uuid (bytes), não por UUID.compareTo do Java.
		List<String> expected = jdbcTemplate.queryForList(
				"SELECT id::text FROM applications WHERE job_id = ? ORDER BY created_at DESC, id DESC", String.class,
				UUID.fromString(jobId));

		for (int attempt = 0; attempt < 3; attempt++) {
			String body = as(company, get(path(jobId))).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			List<String> actual = JsonPath.read(body, "$.content[*].id");
			assertEquals(expected, actual);
		}
		as(company, get(path(jobId)))
				.andExpect(jsonPath("$.content[0].student.fullName").value("Mais nova"))
				.andExpect(jsonPath("$.content[3].student.fullName").value("Mais antiga"));
	}

	@Test
	void paginationUsesTheDecidedDefaultsAndLimits() throws Exception {
		for (int i = 0; i < 12; i++) {
			insert(student("Candidato " + i, List.of(java), "BACKEND"), jobId, "SUBMITTED", "PLATFORM",
					base.plus(i, ChronoUnit.HOURS));
		}

		as(company, get(path(jobId)))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.content.length()").value(10))
				.andExpect(jsonPath("$.totalElements").value(12))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content[0].student.fullName").value("Candidato 11"));
		as(company, get(path(jobId)).param("page", "1"))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[1].student.fullName").value("Candidato 0"));
		as(company, get(path(jobId)).param("page", "9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(12));
		as(company, get(path(jobId)).param("size", "500")).andExpect(jsonPath("$.size").value(50))
				.andExpect(jsonPath("$.content.length()").value(12));
		as(company, get(path(jobId)).param("size", "0")).andExpect(jsonPath("$.size").value(1));
		as(company, get(path(jobId)).param("page", "-3")).andExpect(jsonPath("$.page").value(0));
	}

	@Test
	void statusFilterWorksAndEveryStatusIsVisibleWithoutIt() throws Exception {
		String[] statuses = { "SUBMITTED", "IN_REVIEW", "APPROVED", "REJECTED", "SUSPENDED" };
		for (int i = 0; i < statuses.length; i++) {
			insert(student("Candidato " + statuses[i], List.of(java), "BACKEND"), jobId, statuses[i], "PLATFORM",
					base.plus(i, ChronoUnit.HOURS));
		}

		as(company, get(path(jobId)))
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.content[?(@.status == 'SUSPENDED')].student.fullName")
						.value("Candidato SUSPENDED"))
				.andExpect(jsonPath("$.content[?(@.status == 'REJECTED')].student.fullName")
						.value("Candidato REJECTED"));
		for (String wanted : statuses) {
			as(company, get(path(jobId)).param("status", wanted))
					.andExpect(jsonPath("$.totalElements").value(1))
					.andExpect(jsonPath("$.content[0].status").value(wanted));
		}
		as(company, get(path(jobId)).param("status", "ARCHIVED")).andExpect(status().isBadRequest());
	}

	@Test
	void matchScoreUsesTheCurrentBaseResumeAndNeverThePdf() throws Exception {
		User ana = student("Ana Souza", List.of(java, docker), "BACKEND");
		UUID pdfApplication = insert(ana, jobId, "SUBMITTED", "PDF", base);

		// A candidatura é PDF, mas a nota vem só do currículo-base e é a mesma que a estudante vê em
		// GET /matching/jobs/{id}: o cálculo é um só (MatchingService), sem lógica duplicada.
		as(company, get(path(jobId))).andExpect(jsonPath("$.content[0].resumeType").value("PDF"))
				.andExpect(jsonPath("$.content[0].matchScore").value(100));
		assertSameAsTheStudentsOwnMatch(ana, 100);

		// O score é o atual: mudar o currículo-base muda a nota, sem tocar na candidatura enviada.
		as(ana, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON)
				.content("{\"interestAreas\": [\"DATA\"]}")).andExpect(status().isOk());
		as(company, get(path(jobId)))
				.andExpect(jsonPath("$.content[0].id").value(pdfApplication.toString()))
				.andExpect(jsonPath("$.content[0].matchScore").value(80));
		assertSameAsTheStudentsOwnMatch(ana, 80);

		as(ana, put("/api/v1/students/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [\"%s\"]}".formatted(python))).andExpect(status().isOk());
		// Sem Java (obrigatória), sem Docker e fora dos interesses: só os 15 pontos de modelo e localização.
		as(company, get(path(jobId))).andExpect(jsonPath("$.content[0].matchScore").value(15));
		assertSameAsTheStudentsOwnMatch(ana, 15);
	}

	@Test
	void matchScoreIsNullWhenTheBaseResumeHasNothingToCompare() throws Exception {
		User empty = createStudentProfileOnly("Perfil Vazio");
		User full = student("Ana Souza", List.of(java), "BACKEND");
		insert(empty, jobId, "SUBMITTED", "PLATFORM", base);
		insert(full, jobId, "SUBMITTED", "PLATFORM", base.plusSeconds(60));

		as(company, get(path(jobId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].student.fullName").value("Ana Souza"))
				// Só Java (obrigatória) e área compatível: 45 + 0 desejável + 20 + 15 = 80.
				.andExpect(jsonPath("$.content[0].matchScore").value(80))
				.andExpect(jsonPath("$.content[1].student.fullName").value("Perfil Vazio"))
				.andExpect(jsonPath("$.content[1].matchScore").value(nullValue()))
				.andExpect(jsonPath("$.content[1].length()").value(7));
		// Só competências do catálogo, ou só áreas de interesse, já bastam para haver o que comparar.
		User onlyInterest = createStudentProfileOnly("Só interesse");
		as(onlyInterest, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON)
				.content("{\"interestAreas\": [\"BACKEND\"]}")).andExpect(status().isOk());
		insert(onlyInterest, jobId, "SUBMITTED", "PLATFORM", base.plusSeconds(120));
		as(company, get(path(jobId)).param("size", "3"))
				.andExpect(jsonPath("$.content[0].student.fullName").value("Só interesse"))
				.andExpect(jsonPath("$.content[0].matchScore").isNumber());
	}

	@Test
	void companyWithoutOwnershipOrWithAnInvalidJobGetsNotFound() throws Exception {
		insert(student("Ana Souza", List.of(java), "BACKEND"), jobId, "SUBMITTED", "PLATFORM", base);

		as(createCompany("Outra Empresa"), get(path(jobId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(company, get(path(UUID.randomUUID().toString())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(createUser(UserRole.COMPANY), get(path(jobId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMPANY_PROFILE_NOT_FOUND"));

		as(company, delete("/api/v1/jobs/" + jobId)).andExpect(status().isNoContent());
		as(company, get(path(jobId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
	}

	@Test
	void onlyTheOwningCompanyRoleCanUseTheCompanyRoute() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			as(createUser(role), get(path(jobId))).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(path(jobId))).andExpect(status().isUnauthorized());

		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", company.getId());
		entityManager.clear();
		as(company, get(path(jobId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
	}

	@Test
	void adminListsTheSameDataThroughTheReadOnlyAdminRoute() throws Exception {
		User ana = student("Ana Souza", List.of(java, docker), "BACKEND");
		User bruno = student("Bruno Lima", List.of(java), "BACKEND");
		insert(ana, jobId, "SUSPENDED", "PLATFORM", base);
		insert(bruno, jobId, "REJECTED", "PDF", base.plusSeconds(60));
		User admin = createUser(UserRole.ADMIN);

		as(admin, get(adminPath(jobId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].student.fullName").value("Bruno Lima"))
				.andExpect(jsonPath("$.content[0].status").value("REJECTED"))
				.andExpect(jsonPath("$.content[0].matchScore").value(80))
				.andExpect(jsonPath("$.content[1].student.fullName").value("Ana Souza"))
				.andExpect(jsonPath("$.content[1].matchScore").value(100))
				.andExpect(jsonPath("$.content[0].student.email").doesNotExist())
				.andExpect(content().string(not(containsString(ana.getEmail()))));
		as(admin, get(adminPath(jobId)).param("status", "SUSPENDED").param("size", "1"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.content[0].student.fullName").value("Ana Souza"));
		// Somente leitura e sem auditoria: nenhuma outra operação existe nesta rota.
		as(admin, post(adminPath(jobId))).andExpect(status().isMethodNotAllowed());
		as(admin, put(adminPath(jobId)).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isMethodNotAllowed());
		as(admin, delete(adminPath(jobId))).andExpect(status().isMethodNotAllowed());
		assertEquals(0L, jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_logs", Long.class));
	}

	@Test
	void adminRouteRejectsOtherRolesAndHidesInvalidJobs() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.COMPANY, UserRole.UNIVERSITY }) {
			as(createUser(role), get(adminPath(jobId))).andExpect(status().isForbidden());
		}
		as(company, get(adminPath(jobId))).andExpect(status().isForbidden());
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

	@Test
	void listingLoadsStudentsAndScoresWithoutOneQueryPerRow() throws Exception {
		Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		insert(student("Primeira", List.of(java, docker), "BACKEND"), jobId, "SUBMITTED", "PLATFORM", base);
		long[] few = measure(statistics);

		for (int i = 0; i < 5; i++) {
			insert(student("Candidato " + i, List.of(java, python), "DATA"), jobId, "SUBMITTED", "PLATFORM",
					base.plus(i + 1, ChronoUnit.HOURS));
		}
		long[] many = measure(statistics);

		// Mais linhas não podem significar mais consultas nem mais entidades carregadas uma a uma.
		assertEquals(few[0], many[0], "comandos SQL preparados");
		assertEquals(few[1], many[1], "entidades carregadas por id");
	}

	private void assertSameAsTheStudentsOwnMatch(User student, int expected) throws Exception {
		as(student, get("/api/v1/matching/jobs/" + jobId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.match.score").value(expected));
	}

	private long[] measure(Statistics statistics) throws Exception {
		entityManager.flush();
		entityManager.clear();
		statistics.clear();
		as(company, get(path(jobId))).andExpect(status().isOk());
		return new long[] { statistics.getPrepareStatementCount(), statistics.getEntityFetchCount() };
	}

	private static String path(String id) {
		return "/api/v1/jobs/" + id + "/applications";
	}

	private static String adminPath(String id) {
		return "/api/v1/admin/jobs/" + id + "/applications";
	}

	/** Insere direto no banco para controlar status e data; o envio pela API é coberto nos testes de candidatura. */
	private UUID insert(User owner, String job, String status, String resumeType, Instant createdAt) {
		UUID studentId = studentProfileId(owner);
		Timestamp when = Timestamp.from(createdAt);
		if (resumeType.equals("PDF")) {
			return jdbcTemplate.queryForObject("""
					INSERT INTO applications (student_id, job_id, resume_type, status, storage_key, original_filename,
					                          file_size, content_type, created_at, updated_at)
					VALUES (?, ?, 'PDF', ?, ?, 'curriculo.pdf', 10, 'application/pdf', ?, ?) RETURNING id
					""", UUID.class, studentId, UUID.fromString(job), status, UUID.randomUUID() + ".pdf", when, when);
		}
		return jdbcTemplate.queryForObject("""
				INSERT INTO applications (student_id, job_id, resume_type, status, resume_snapshot, created_at, updated_at)
				VALUES (?, ?, 'PLATFORM', ?, '{}'::jsonb, ?, ?) RETURNING id
				""", UUID.class, studentId, UUID.fromString(job), status, when, when);
	}

	private UUID studentProfileId(User owner) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class, owner.getId());
	}

	private User createStudentProfileOnly(String name) throws Exception {
		User owner = createUser(UserRole.STUDENT);
		as(owner, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON).content("""
				{"fullName": "%s", "course": "Ciência da Computação", "institution": "UFAPE",
				 "location": "Garanhuns, PE", "summary": "Resumo de %s."}
				""".formatted(name, name))).andExpect(status().isOk());
		return owner;
	}

	private User student(String name, List<UUID> skills, String interestArea) throws Exception {
		User owner = createStudentProfileOnly(name);
		as(owner, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON)
				.content("{\"interestAreas\": [\"%s\"]}".formatted(interestArea))).andExpect(status().isOk());
		String ids = String.join(", ", skills.stream().map(id -> "\"" + id + "\"").toList());
		as(owner, put("/api/v1/students/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [%s]}".formatted(ids))).andExpect(status().isOk());
		return owner;
	}

	private String publishJob(User owner, String title) throws Exception {
		String response = as(owner, post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "%s", "description": "Descrição da vaga.", "area": "BACKEND", "location": null,
				 "workModel": "REMOTE", "level": "INTERNSHIP", "requiredSkillIds": ["%s"], "desiredSkillIds": ["%s"]}
				""".formatted(title, java, docker)))
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

	private ResultActions as(User user, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}
}
