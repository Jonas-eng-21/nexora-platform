package br.com.nexora.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import br.com.nexora.administration.service.AdminUserService;
import br.com.nexora.administration.service.AuditLogService;
import br.com.nexora.applications.service.ApplicationReactivationService;
import br.com.nexora.shared.security.TokenService;
import br.com.nexora.universities.service.AffiliationDecisionService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;

/**
 * Atomicidade e concorrência da reativação. Fica FORA de {@code @Transactional} de propósito (veja
 * {@code ApplicationSuspensionTransactionTests}): sem transação de teste há transações reais concorrentes e o
 * desfazimento de uma falha é de verdade. Cada cenário segura uma transação aberta (pausada antes do commit) e
 * confere que a outra operação espera e qual é o resultado depois. Dados removidos ao fim (prefixo {@value #PREFIX}).
 */
@SpringBootTest
class ApplicationReactivationTransactionTests {

	private static final String PREFIX = "reactivation33-";
	private static final Instant OLD = Instant.parse("2026-11-01T12:00:00Z");
	private static final long BLOCKED_MILLIS = 1500;

	@MockitoSpyBean
	private AuditLogService auditLogService;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private ApplicationReactivationService reactivationService;

	@Autowired
	private AffiliationDecisionService affiliationDecisionService;

	@Autowired
	private AdminUserService adminUserService;

	private MockMvc mockMvc;
	private TransactionTemplate tx;
	private ExecutorService executor;
	private User admin;
	private User company;
	private User universityAccount;
	private User student;
	private UUID affiliationId;
	private UUID applicationId;

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		tx = new TransactionTemplate(transactionManager);
		executor = Executors.newFixedThreadPool(3);
		admin = createUser(UserRole.ADMIN);
		company = createUser(UserRole.COMPANY);
		as(company, put("/api/v1/companies/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Empresa Reativação\"}")).andExpect(status().isOk());
		UUID java = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = 'java'", UUID.class);
		String job = JsonPath.read(as(company, post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "Vaga Reativação", "description": "Descrição.", "area": "BACKEND", "location": null,
				 "workModel": "REMOTE", "level": "INTERNSHIP", "requiredSkillIds": ["%s"], "desiredSkillIds": []}
				""".formatted(java))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
				"$.id");
		universityAccount = createUser(UserRole.UNIVERSITY);
		UUID universityId = jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'UFAPE', 'APPROVED') RETURNING id",
				UUID.class, universityAccount.getId());
		student = createUser(UserRole.STUDENT);
		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Ana Reativação\"}")).andExpect(status().isOk());
		UUID studentId = jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class,
				student.getId());
		affiliationId = jdbcTemplate.queryForObject("""
				INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, 'APPROVED')
				RETURNING id""", UUID.class, studentId, universityId);
		Timestamp when = Timestamp.from(OLD);
		// Suspensa há algum tempo, mas o estudante voltou a ser elegível: o cenário em que a reativação faz sentido.
		applicationId = jdbcTemplate.queryForObject("""
				INSERT INTO applications (student_id, job_id, resume_type, status, status_reason, resume_snapshot,
				                          created_at, updated_at, suspended_from_status, suspension_reason, suspended_at)
				VALUES (?, ?, 'PLATFORM', 'SUSPENDED', 'Entrevista agendada.', '{}'::jsonb, ?, ?, 'IN_REVIEW',
				        'ACADEMIC_AFFILIATION_REVOKED', ?) RETURNING id
				""", UUID.class, studentId, UUID.fromString(job), when, when, when);
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
		cleanUp();
	}

	// ---------------------------------------------------------------- atomicidade

	@Test
	void aFailingAuditRecordUndoesTheAdminReactivation() throws Exception {
		tx.executeWithoutResult(transaction -> doThrow(new IllegalStateException("auditoria indisponível"))
				.when(auditLogService).record(any(), any(), any(), any(), any(), any(), any()));

		as(admin, post("/api/v1/admin/applications/" + applicationId + "/reactivate"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

		// A candidatura continua suspensa, com todos os dados da suspensão e sem rastro de auditoria.
		Map<String, Object> row = row();
		assertEquals("SUSPENDED", row.get("status"));
		assertEquals("IN_REVIEW", row.get("suspended_from_status"));
		assertEquals("ACADEMIC_AFFILIATION_REVOKED", row.get("suspension_reason"));
		assertNotNull(row.get("suspended_at"));
		assertEquals(OLD, ((Timestamp) row.get("updated_at")).toInstant());
		assertEquals(0L, auditRows());
	}

	@Test
	void theSameReactivationSucceedsAndIsAuditedWhenTheAuditWorks() throws Exception {
		as(admin, post("/api/v1/admin/applications/" + applicationId + "/reactivate"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_REVIEW"));

		Map<String, Object> row = row();
		assertEquals("IN_REVIEW", row.get("status"));
		assertNull(row.get("suspended_from_status"));
		assertEquals(1L, auditRows());
	}

	// ---------------------------------------------------------------- concorrência

	@Test
	void twoSimultaneousReactivationsRestoreTheApplicationOnlyOnce() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		Future<?> first = inFlight(() -> reactivationService.reactivateAsCompany(company.getId(), applicationId),
				release);

		Future<MvcResult> second = executor.submit(() -> as(company, reactivate()).andReturn());
		MvcResult result = finish(second, first, release);

		// A segunda esperou a primeira e encontrou a candidatura já fora da suspensão.
		assertEquals(409, result.getResponse().getStatus());
		assertEquals("APPLICATION_NOT_SUSPENDED", JsonPath.read(result.getResponse().getContentAsString(), "$.code"));
		Map<String, Object> row = row();
		assertEquals("IN_REVIEW", row.get("status"));
		assertNull(row.get("suspended_from_status"));
		assertNull(row.get("suspension_reason"));
		assertNull(row.get("suspended_at"));
		assertEquals("Entrevista agendada.", row.get("status_reason"));
	}

	@Test
	void aReactivationWaitsForAnInFlightRevocationAndIsRefused() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		Future<?> revocation = inFlight(
				() -> affiliationDecisionService.revokeAsUniversity(universityAccount.getId(), affiliationId, "Trancou."),
				release);

		Future<MvcResult> reactivation = executor.submit(() -> as(company, reactivate()).andReturn());
		MvcResult result = finish(reactivation, revocation, release);

		// Sem a trava, a reativação leria o vínculo ainda aprovado e terminaria agora, deixando uma candidatura
		// em andamento de um estudante cujo vínculo acabou de ser revogado.
		assertEquals(403, result.getResponse().getStatus());
		assertEquals("STUDENT_NOT_VALIDATED", JsonPath.read(result.getResponse().getContentAsString(), "$.code"));
		assertEquals("SUSPENDED", row().get("status"));
		assertEquals("REVOKED", affiliationStatus());
	}

	@Test
	void aRevocationWaitsForAnInFlightReactivationAndSuspendsTheApplicationAgain() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		Future<?> reactivation = inFlight(() -> reactivationService.reactivateAsCompany(company.getId(), applicationId),
				release);

		Future<MvcResult> revocation = executor.submit(() -> as(universityAccount, post("/api/v1/affiliations/"
				+ affiliationId + "/revoke").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Trancou.\"}")).andReturn());
		MvcResult result = finish(revocation, reactivation, release);

		// A reativação concluiu antes; a revogação, que veio depois, enxerga a candidatura em andamento e a suspende.
		assertEquals(200, result.getResponse().getStatus());
		Map<String, Object> row = row();
		assertEquals("SUSPENDED", row.get("status"));
		assertEquals("IN_REVIEW", row.get("suspended_from_status"));
		assertEquals("ACADEMIC_AFFILIATION_REVOKED", row.get("suspension_reason"));
		assertTrue(((Timestamp) row.get("suspended_at")).toInstant().isAfter(OLD), "suspended_at renovado");
		assertEquals("REVOKED", affiliationStatus());
	}

	@Test
	void aReactivationWaitsForAnInFlightUniversitySuspensionAndIsRefused() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		Future<?> suspension = inFlight(
				() -> adminUserService.suspend(admin.getId(), universityAccount.getId(), "Irregularidade."), release);

		Future<MvcResult> reactivation = executor.submit(() -> as(company, reactivate()).andReturn());
		MvcResult result = finish(reactivation, suspension, release);

		assertEquals(403, result.getResponse().getStatus());
		assertEquals("SUSPENDED", row().get("status"));
		assertEquals("SUSPENDED", jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class,
				universityAccount.getId()));
	}

	@Test
	void aUniversitySuspensionWaitsForAnInFlightReactivationAndSuspendsTheApplicationAgain() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		Future<?> reactivation = inFlight(() -> reactivationService.reactivateAsCompany(company.getId(), applicationId),
				release);

		Future<MvcResult> suspension = executor.submit(() -> as(admin, post("/api/v1/admin/users/"
				+ universityAccount.getId() + "/suspend").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Irregularidade.\"}")).andReturn());
		MvcResult result = finish(suspension, reactivation, release);

		assertEquals(200, result.getResponse().getStatus());
		Map<String, Object> row = row();
		assertEquals("SUSPENDED", row.get("status"));
		assertEquals("IN_REVIEW", row.get("suspended_from_status"));
		assertEquals("UNIVERSITY_SUSPENDED", row.get("suspension_reason"));
		assertEquals("APPROVED", affiliationStatus());
	}

	@Test
	void aStatusChangeWaitsForAnInFlightReactivationAndThenWorksOnTheRestoredStatus() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		Future<?> reactivation = inFlight(() -> reactivationService.reactivateAsCompany(company.getId(), applicationId),
				release);

		Future<MvcResult> change = executor.submit(() -> as(company, patch("/api/v1/applications/" + applicationId
				+ "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"APPROVED\"}"))
				.andReturn());
		MvcResult result = finish(change, reactivation, release);

		// A decisão esperou a reativação e então agiu sobre o estado restaurado (IN_REVIEW para APPROVED).
		assertEquals(200, result.getResponse().getStatus());
		Map<String, Object> row = row();
		assertEquals("APPROVED", row.get("status"));
		assertNull(row.get("suspended_from_status"));
	}

	// ---------------------------------------------------------------- apoio

	/** Executa a operação numa transação aberta e a mantém assim (sem commit) até o latch ser liberado. */
	private Future<?> inFlight(Runnable work, CountDownLatch release) throws InterruptedException {
		CountDownLatch started = new CountDownLatch(1);
		Future<?> future = executor.submit(() -> tx.executeWithoutResult(status -> {
			work.run();
			started.countDown();
			await(release);
		}));
		assertTrue(started.await(10, TimeUnit.SECONDS), "a operação em andamento não chegou ao ponto de pausa");
		return future;
	}

	/** Confere que a requisição fica bloqueada enquanto a outra transação está aberta e devolve o resultado depois. */
	private MvcResult finish(Future<MvcResult> request, Future<?> inFlight, CountDownLatch release) throws Exception {
		assertThrows(TimeoutException.class, () -> request.get(BLOCKED_MILLIS, TimeUnit.MILLISECONDS));
		release.countDown();
		inFlight.get(10, TimeUnit.SECONDS);
		return request.get(10, TimeUnit.SECONDS);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private AbstractMockHttpServletRequestBuilder<?> reactivate() {
		return post("/api/v1/applications/" + applicationId + "/reactivate");
	}

	private Map<String, Object> row() {
		return jdbcTemplate.queryForMap("""
				SELECT status, status_reason, suspended_from_status, suspension_reason, suspended_at, updated_at
				FROM applications WHERE id = ?""", applicationId);
	}

	private String affiliationStatus() {
		return jdbcTemplate.queryForObject("SELECT status FROM university_affiliations WHERE id = ?", String.class,
				affiliationId);
	}

	private long auditRows() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_logs WHERE admin_id = ?", Long.class,
				admin.getId());
	}

	private User createUser(UserRole role) {
		return userRepository.save(new User(PREFIX + UUID.randomUUID() + "@nexora.test", "hash", role,
				AccountStatus.ACTIVE));
	}

	private ResultActions as(User user, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}

	private void cleanUp() {
		String like = PREFIX + "%";
		jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE admin_id IN (SELECT id FROM users WHERE email LIKE ?)",
				like);
		jdbcTemplate.update("""
				DELETE FROM applications WHERE student_id IN
				  (SELECT id FROM students WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?))""", like);
		jdbcTemplate.update("""
				DELETE FROM university_affiliations WHERE student_id IN
				  (SELECT id FROM students WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?))""", like);
		jdbcTemplate.update("""
				DELETE FROM job_skills WHERE job_id IN (SELECT j.id FROM jobs j JOIN companies c ON c.id = j.company_id
				  WHERE c.user_id IN (SELECT id FROM users WHERE email LIKE ?))""", like);
		jdbcTemplate.update("""
				DELETE FROM jobs WHERE company_id IN
				  (SELECT id FROM companies WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?))""", like);
		jdbcTemplate.update("DELETE FROM students WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", like);
		jdbcTemplate.update("DELETE FROM companies WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", like);
		jdbcTemplate.update("DELETE FROM universities WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
				like);
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", like);
	}
}
