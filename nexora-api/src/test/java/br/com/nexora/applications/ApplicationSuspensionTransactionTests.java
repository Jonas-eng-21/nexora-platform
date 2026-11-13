package br.com.nexora.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
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
import br.com.nexora.applications.dto.ChangeApplicationStatusRequest;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.entity.SuspensionReason;
import br.com.nexora.applications.service.ApplicationStatusService;
import br.com.nexora.applications.service.ApplicationSuspensionService;
import br.com.nexora.shared.security.TokenService;
import br.com.nexora.universities.service.AffiliationDecisionService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;

/**
 * Atomicidade e concorrência da suspensão automática. Fica FORA de {@code @Transactional} de propósito: numa
 * transação de teste, que sempre faz rollback, não daria para distinguir um desfazimento real de um efeito da
 * própria transação do teste, nem haveria duas transações concorrentes. Os dados são gravados de verdade e
 * removidos ao fim de cada teste (prefixo de e-mail {@value #PREFIX}).
 */
@SpringBootTest
class ApplicationSuspensionTransactionTests {

	private static final String PREFIX = "suspension32-";
	private static final Instant OLD = Instant.parse("2026-11-01T12:00:00Z");
	private static final long BLOCKED_MILLIS = 1500;

	@MockitoSpyBean
	private ApplicationSuspensionService suspensionService;

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
	private AffiliationDecisionService affiliationDecisionService;

	@Autowired
	private AdminUserService adminUserService;

	@Autowired
	private ApplicationStatusService applicationStatusService;

	private MockMvc mockMvc;
	private TransactionTemplate tx;
	private ExecutorService executor;
	private User admin;
	private User company;
	private User universityAccount;
	private User student;
	private UUID studentId;
	private UUID affiliationId;
	private String job1;
	private String job2;
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
				.content("{\"name\": \"Empresa Suspensão\"}")).andExpect(status().isOk());
		job1 = publishJob("Vaga 1");
		job2 = publishJob("Vaga 2");
		universityAccount = createUser(UserRole.UNIVERSITY);
		UUID universityId = jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'UFAPE', 'APPROVED') RETURNING id",
				UUID.class, universityAccount.getId());
		student = createUser(UserRole.STUDENT);
		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Ana Suspensão\"}")).andExpect(status().isOk());
		studentId = jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class,
				student.getId());
		affiliationId = jdbcTemplate.queryForObject("""
				INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, 'APPROVED')
				RETURNING id""", UUID.class, studentId, universityId);
		Timestamp when = Timestamp.from(OLD);
		applicationId = jdbcTemplate.queryForObject("""
				INSERT INTO applications (student_id, job_id, resume_type, status, resume_snapshot, created_at, updated_at)
				VALUES (?, ?, 'PLATFORM', 'IN_REVIEW', '{}'::jsonb, ?, ?) RETURNING id
				""", UUID.class, studentId, UUID.fromString(job1), when, when);
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
		cleanUp();
	}

	// ---------------------------------------------------------------- atomicidade

	@Test
	void aFailureSuspendingApplicationsUndoesTheAffiliationRevocation() throws Exception {
		makeSuspendingFail();

		as(universityAccount, post("/api/v1/affiliations/" + affiliationId + "/revoke")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Trancou a matrícula.\"}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

		// Nem o vínculo revogado nem a candidatura suspensa: ou tudo, ou nada.
		assertEquals("APPROVED", affiliationStatus());
		assertEquals("IN_REVIEW", applicationStatus());
		assertEquals(0L, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM university_affiliations WHERE revoked_at IS NOT NULL", Long.class));
	}

	@Test
	void aFailureSuspendingApplicationsUndoesTheAdminRevocationAndItsAudit() throws Exception {
		makeSuspendingFail();

		as(admin, post("/api/v1/admin/affiliations/" + affiliationId + "/revoke")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Encerrado.\"}"))
				.andExpect(status().isInternalServerError());

		assertEquals("APPROVED", affiliationStatus());
		assertEquals("IN_REVIEW", applicationStatus());
		assertEquals(0L, auditRows());
	}

	@Test
	void aFailureSuspendingApplicationsLeavesTheUniversityActive() throws Exception {
		makeSuspendingFail();

		as(admin, post("/api/v1/admin/users/" + universityAccount.getId() + "/suspend")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Irregularidade.\"}"))
				.andExpect(status().isInternalServerError());

		assertEquals("ACTIVE", jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class,
				universityAccount.getId()));
		assertEquals("IN_REVIEW", applicationStatus());
		assertEquals(0L, auditRows());
	}

	@Test
	void theSameRequestsSucceedAndAreAtomicWhenNothingFails() throws Exception {
		as(universityAccount, post("/api/v1/affiliations/" + affiliationId + "/revoke")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Trancou a matrícula.\"}"))
				.andExpect(status().isOk());

		assertEquals("REVOKED", affiliationStatus());
		assertEquals("SUSPENDED", applicationStatus());
	}

	@Test
	void suspendingTwiceIsHarmlessAndKeepsTheFirstSuspension() {
		int first = suspendInTransaction();
		Timestamp firstAt = suspendedAt();
		int second = suspendInTransaction();

		assertEquals(1, first);
		assertEquals(0, second);
		assertEquals(firstAt, suspendedAt());
		assertEquals("IN_REVIEW", jdbcTemplate.queryForObject(
				"SELECT suspended_from_status FROM applications WHERE id = ?", String.class, applicationId));
	}

	// ---------------------------------------------------------------- concorrência

	@Test
	void anApplicationWaitsForAnInFlightRevocationAndIsThenRefused() throws Exception {
		CountDownLatch revoked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> revocation = executor.submit(() -> tx.executeWithoutResult(status -> {
			affiliationDecisionService.revokeAsUniversity(universityAccount.getId(), affiliationId, "Trancou.");
			revoked.countDown();
			await(release);
		}));
		assertTrue(revoked.await(10, TimeUnit.SECONDS));

		Future<MvcResult> application = executor.submit(() -> as(student, applyTo(job2)).andReturn());
		// Sem a trava, a candidatura leria o vínculo ainda aprovado e terminaria agora, deixando uma candidatura
		// em andamento de um estudante sem vínculo — a que a revogação em lote nunca enxergaria.
		assertThrows(TimeoutException.class, () -> application.get(BLOCKED_MILLIS, TimeUnit.MILLISECONDS));

		release.countDown();
		revocation.get(10, TimeUnit.SECONDS);
		MvcResult result = application.get(10, TimeUnit.SECONDS);
		assertEquals(403, result.getResponse().getStatus());
		assertEquals("STUDENT_NOT_VALIDATED", JsonPath.read(result.getResponse().getContentAsString(), "$.code"));
		assertEquals(1L, jdbcTemplate.queryForObject("SELECT count(*) FROM applications WHERE student_id = ?",
				Long.class, studentId));
	}

	@Test
	void anApplicationWaitsForAnInFlightUniversitySuspensionAndIsThenRefused() throws Exception {
		CountDownLatch suspended = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> suspension = executor.submit(() -> tx.executeWithoutResult(status -> {
			adminUserService.suspend(admin.getId(), universityAccount.getId(), "Irregularidade.");
			suspended.countDown();
			await(release);
		}));
		assertTrue(suspended.await(10, TimeUnit.SECONDS));

		Future<MvcResult> application = executor.submit(() -> as(student, applyTo(job2)).andReturn());
		assertThrows(TimeoutException.class, () -> application.get(BLOCKED_MILLIS, TimeUnit.MILLISECONDS));

		release.countDown();
		suspension.get(10, TimeUnit.SECONDS);
		assertEquals(403, application.get(10, TimeUnit.SECONDS).getResponse().getStatus());
		assertEquals(1L, jdbcTemplate.queryForObject("SELECT count(*) FROM applications WHERE student_id = ?",
				Long.class, studentId));
	}

	@Test
	void aStatusChangeWaitsForAnInFlightRevocationAndFindsTheApplicationSuspended() throws Exception {
		CountDownLatch revoked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> revocation = executor.submit(() -> tx.executeWithoutResult(status -> {
			affiliationDecisionService.revokeAsUniversity(universityAccount.getId(), affiliationId, "Trancou.");
			revoked.countDown();
			await(release);
		}));
		assertTrue(revoked.await(10, TimeUnit.SECONDS));

		Future<MvcResult> change = executor.submit(() -> as(company, patch("/api/v1/applications/" + applicationId
				+ "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"APPROVED\"}"))
				.andReturn());
		assertThrows(TimeoutException.class, () -> change.get(BLOCKED_MILLIS, TimeUnit.MILLISECONDS));

		release.countDown();
		revocation.get(10, TimeUnit.SECONDS);
		MvcResult result = change.get(10, TimeUnit.SECONDS);
		// A empresa não consegue aprovar uma candidatura que a revogação acabou de suspender.
		assertEquals(409, result.getResponse().getStatus());
		assertEquals("APPLICATION_SUSPENDED", JsonPath.read(result.getResponse().getContentAsString(), "$.code"));
		assertEquals("SUSPENDED", applicationStatus());
	}

	@Test
	void aRevocationWaitsForAnInFlightStatusChangeAndNeverSuspendsTheDecidedApplication() throws Exception {
		CountDownLatch decided = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> decision = executor.submit(() -> tx.executeWithoutResult(status -> {
			applicationStatusService.changeAsCompany(company.getId(), applicationId,
					new ChangeApplicationStatusRequest(ApplicationStatus.APPROVED, "Aprovado."));
			decided.countDown();
			await(release);
		}));
		assertTrue(decided.await(10, TimeUnit.SECONDS));

		Future<MvcResult> revocation = executor.submit(() -> as(universityAccount, post("/api/v1/affiliations/"
				+ affiliationId + "/revoke").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Trancou.\"}")).andReturn());
		assertThrows(TimeoutException.class, () -> revocation.get(BLOCKED_MILLIS, TimeUnit.MILLISECONDS));

		release.countDown();
		decision.get(10, TimeUnit.SECONDS);
		assertEquals(200, revocation.get(10, TimeUnit.SECONDS).getResponse().getStatus());
		// A aprovação veio antes da revogação: a candidatura final não é suspensa nem alterada.
		assertEquals("APPROVED", applicationStatus());
		assertEquals("REVOKED", affiliationStatus());
		assertEquals(0L, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM applications WHERE suspension_reason IS NOT NULL", Long.class));
	}

	// ---------------------------------------------------------------- apoio

	private void makeSuspendingFail() {
		// O stub é preparado dentro de uma transação porque o método exige uma (MANDATORY) e a chamada de
		// preparo passa pelo proxy transacional antes de chegar ao Mockito. O spy volta ao normal após o teste.
		tx.executeWithoutResult(transaction -> doThrow(new IllegalStateException("suspensão indisponível"))
				.when(suspensionService).suspendInProgress(any(), any()));
	}

	private int suspendInTransaction() {
		Integer count = tx.execute(transaction -> suspensionService.suspendInProgress(Set.of(studentId),
				SuspensionReason.ACADEMIC_AFFILIATION_REVOKED));
		assertNotNull(count);
		return count;
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static AbstractMockHttpServletRequestBuilder<?> applyTo(String job) {
		return multipart("/api/v1/jobs/" + job + "/applications").param("resumeType", "PLATFORM");
	}

	private String affiliationStatus() {
		return jdbcTemplate.queryForObject("SELECT status FROM university_affiliations WHERE id = ?", String.class,
				affiliationId);
	}

	private String applicationStatus() {
		return jdbcTemplate.queryForObject("SELECT status FROM applications WHERE id = ?", String.class,
				applicationId);
	}

	private Timestamp suspendedAt() {
		return jdbcTemplate.queryForObject("SELECT suspended_at FROM applications WHERE id = ?", Timestamp.class,
				applicationId);
	}

	private long auditRows() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_logs WHERE admin_id = ?", Long.class,
				admin.getId());
	}

	private String publishJob(String title) throws Exception {
		UUID java = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = 'java'", UUID.class);
		String response = as(company, post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "%s", "description": "Descrição.", "area": "BACKEND", "location": null,
				 "workModel": "REMOTE", "level": "INTERNSHIP", "requiredSkillIds": ["%s"], "desiredSkillIds": []}
				""".formatted(title, java))).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
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
