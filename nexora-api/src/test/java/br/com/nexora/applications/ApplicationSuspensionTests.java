package br.com.nexora.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
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

/**
 * Comportamento da suspensão automática. Roda dentro de uma transação de teste (rollback no fim): o que prova a
 * atomicidade e a concorrência está em {@code ApplicationSuspensionTransactionTests}, que não usa transação de teste.
 */
@SpringBootTest
@Transactional
class ApplicationSuspensionTests {

	private static final Instant OLD = Instant.parse("2026-11-01T12:00:00Z");

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

	@Autowired
	private ApplicationEventMulticaster eventMulticaster;

	private MockMvc mockMvc;
	private User company;
	private User admin;
	private User universityA;
	private User universityB;
	private UUID universityAId;
	private UUID universityBId;
	private User student1;
	private User student2;
	private User studentB;
	private UUID affiliation1;
	private UUID affiliation2;
	private UUID affiliationB;
	private String job1;
	private String job2;
	private String job3;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		admin = createUser(UserRole.ADMIN);
		company = createCompany("Tech Garanhuns");
		universityA = createUser(UserRole.UNIVERSITY);
		universityB = createUser(UserRole.UNIVERSITY);
		universityAId = createUniversity(universityA, "UFAPE");
		universityBId = createUniversity(universityB, "UPE");
		student1 = createStudent("Ana Souza");
		student2 = createStudent("Bruno Lima");
		studentB = createStudent("Carla Dias");
		affiliation1 = affiliate(student1, universityAId);
		affiliation2 = affiliate(student2, universityAId);
		affiliationB = affiliate(studentB, universityBId);
		job1 = publishJob(company, "Vaga 1");
		job2 = publishJob(company, "Vaga 2");
		job3 = publishJob(company, "Vaga 3");
	}

	@Test
	void universityRevocationSuspendsTheStudentsInProgressApplications() throws Exception {
		UUID submitted = application(student1, job1, "SUBMITTED");
		UUID inReview = application(student1, job2, "IN_REVIEW");
		jdbcTemplate.update("UPDATE applications SET status_reason = 'Entrevista agendada.' WHERE id = ?", inReview);
		Instant before = Instant.now().minusSeconds(5);

		as(universityA, post("/api/v1/affiliations/" + affiliation1 + "/revoke").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Trancou a matrícula.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVOKED"));

		Map<String, Object> first = row(submitted);
		assertEquals("SUSPENDED", first.get("status"));
		assertEquals("SUBMITTED", first.get("suspended_from_status"));
		assertEquals("ACADEMIC_AFFILIATION_REVOKED", first.get("suspension_reason"));
		Instant suspendedAt = ((Timestamp) first.get("suspended_at")).toInstant();
		assertTrue(suspendedAt.isAfter(before) && suspendedAt.isBefore(Instant.now().plusSeconds(5)), "suspended_at");
		assertTrue(((Timestamp) first.get("updated_at")).toInstant().isAfter(OLD), "updated_at");

		Map<String, Object> second = row(inReview);
		assertEquals("SUSPENDED", second.get("status"));
		// O estado anterior fica guardado para a reativação, e o motivo da decisão ordinária não é tocado.
		assertEquals("IN_REVIEW", second.get("suspended_from_status"));
		assertEquals("Entrevista agendada.", second.get("status_reason"));
	}

	@Test
	void adminRevocationHasTheSameEffectAndIsAuditedOnceWithoutOneEntryPerApplication() throws Exception {
		UUID submitted = application(student1, job1, "SUBMITTED");
		UUID inReview = application(student1, job2, "IN_REVIEW");
		UUID another = application(student1, job3, "SUBMITTED");

		as(admin, post("/api/v1/admin/affiliations/" + affiliation1 + "/revoke").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Vínculo encerrado pela universidade.\"}"))
				.andExpect(status().isOk());

		for (UUID id : new UUID[] { submitted, inReview, another }) {
			assertEquals("SUSPENDED", row(id).get("status"));
			assertEquals("ACADEMIC_AFFILIATION_REVOKED", row(id).get("suspension_reason"));
		}
		assertEquals("IN_REVIEW", row(inReview).get("suspended_from_status"));
		// A auditoria de administração continua a de sempre: uma entrada, da revogação, e nenhuma por candidatura.
		entityManager.flush();
		assertEquals(1L, jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_logs", Long.class));
		assertEquals("AFFILIATION_REVOKED", jdbcTemplate.queryForObject("SELECT action FROM admin_audit_logs",
				String.class));
	}

	@Test
	void finalApplicationsAreNeverTouched() throws Exception {
		UUID approved = application(student1, job1, "APPROVED");
		UUID rejected = application(student1, job2, "REJECTED");
		UUID inReview = application(student1, job3, "IN_REVIEW");

		as(universityA, post("/api/v1/affiliations/" + affiliation1 + "/revoke").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Trancou a matrícula.\"}")).andExpect(status().isOk());

		for (UUID id : new UUID[] { approved, rejected }) {
			Map<String, Object> row = row(id);
			assertNull(row.get("suspended_from_status"));
			assertNull(row.get("suspension_reason"));
			assertNull(row.get("suspended_at"));
			assertEquals(OLD, ((Timestamp) row.get("updated_at")).toInstant());
		}
		assertEquals("APPROVED", row(approved).get("status"));
		assertEquals("REJECTED", row(rejected).get("status"));
		assertEquals("SUSPENDED", row(inReview).get("status"));
	}

	@Test
	void revokingOneLinkLeavesOtherStudentsAndOtherUniversitiesUntouched() throws Exception {
		UUID mine = application(student1, job1, "SUBMITTED");
		UUID sameUniversity = application(student2, job1, "SUBMITTED");
		UUID otherUniversity = application(studentB, job1, "IN_REVIEW");

		as(universityA, post("/api/v1/affiliations/" + affiliation1 + "/revoke").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Trancou a matrícula.\"}")).andExpect(status().isOk());

		assertEquals("SUSPENDED", row(mine).get("status"));
		assertEquals("SUBMITTED", row(sameUniversity).get("status"));
		assertEquals("IN_REVIEW", row(otherUniversity).get("status"));
		assertNull(row(sameUniversity).get("suspension_reason"));
	}

	@Test
	void suspendingAUniversitySuspendsTheApplicationsOfItsLinkedStudentsOnly() throws Exception {
		UUID a1 = application(student1, job1, "SUBMITTED");
		UUID a2 = application(student2, job1, "IN_REVIEW");
		UUID a2Approved = application(student2, job2, "APPROVED");
		UUID b1 = application(studentB, job1, "SUBMITTED");

		as(admin, post("/api/v1/admin/users/" + universityA.getId() + "/suspend").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Irregularidade institucional.\"}")).andExpect(status().isOk());

		assertEquals("SUSPENDED", row(a1).get("status"));
		assertEquals("SUBMITTED", row(a1).get("suspended_from_status"));
		assertEquals("UNIVERSITY_SUSPENDED", row(a1).get("suspension_reason"));
		assertEquals("SUSPENDED", row(a2).get("status"));
		assertEquals("IN_REVIEW", row(a2).get("suspended_from_status"));
		assertEquals("APPROVED", row(a2Approved).get("status"));
		assertEquals("SUBMITTED", row(b1).get("status"));
		assertNull(row(b1).get("suspension_reason"));
	}

	@Test
	void suspendingAUniversityKeepsItsAffiliationsApprovedButIneligible() throws Exception {
		application(student1, job1, "SUBMITTED");

		as(admin, post("/api/v1/admin/users/" + universityA.getId() + "/suspend").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Irregularidade institucional.\"}")).andExpect(status().isOk());

		entityManager.flush();
		// Nenhum vínculo é revogado em lote: o estado original fica preservado.
		assertEquals("APPROVED", jdbcTemplate.queryForObject("SELECT status FROM university_affiliations WHERE id = ?",
				String.class, affiliation1));
		assertEquals("APPROVED", jdbcTemplate.queryForObject("SELECT status FROM university_affiliations WHERE id = ?",
				String.class, affiliation2));
		assertEquals(0L, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM university_affiliations WHERE status = 'REVOKED'", Long.class));
		// ...mas a elegibilidade some enquanto a universidade estiver suspensa.
		as(student1, get("/api/v1/affiliations/me/eligibility")).andExpect(jsonPath("$.eligible").value(false));
	}

	@Test
	void reactivatingTheUniversityDoesNotReactivateApplications() throws Exception {
		UUID id = application(student1, job1, "IN_REVIEW");
		as(admin, post("/api/v1/admin/users/" + universityA.getId() + "/suspend").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Irregularidade institucional.\"}")).andExpect(status().isOk());

		as(admin, post("/api/v1/admin/users/" + universityA.getId() + "/reactivate")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Regularizada.\"}"))
				.andExpect(status().isOk());

		// Nova validação nunca reativa sozinha: só uma operação explícita (outra história) poderá fazê-lo.
		assertEquals("SUSPENDED", row(id).get("status"));
		assertEquals("IN_REVIEW", row(id).get("suspended_from_status"));
		as(student1, get("/api/v1/affiliations/me/eligibility")).andExpect(jsonPath("$.eligible").value(true));
	}

	@Test
	void anAlreadySuspendedApplicationKeepsItsFirstSuspensionData() throws Exception {
		UUID id = application(student1, job1, "SUBMITTED");
		jdbcTemplate.update("""
				UPDATE applications SET status = 'SUSPENDED', suspended_from_status = 'IN_REVIEW',
				  suspension_reason = 'ACADEMIC_AFFILIATION_REVOKED', suspended_at = ? WHERE id = ?
				""", Timestamp.from(OLD), id);
		entityManager.clear();

		as(admin, post("/api/v1/admin/users/" + universityA.getId() + "/suspend").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Irregularidade institucional.\"}")).andExpect(status().isOk());

		Map<String, Object> row = row(id);
		assertEquals("SUSPENDED", row.get("status"));
		assertEquals("IN_REVIEW", row.get("suspended_from_status"));
		assertEquals("ACADEMIC_AFFILIATION_REVOKED", row.get("suspension_reason"));
		assertEquals(OLD, ((Timestamp) row.get("suspended_at")).toInstant());
	}

	@Test
	void suspendingOtherAccountsDoesNotTouchApplications() throws Exception {
		UUID id = application(student1, job1, "SUBMITTED");

		as(admin, post("/api/v1/admin/users/" + company.getId() + "/suspend").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Teste.\"}")).andExpect(status().isOk());

		assertEquals("SUBMITTED", row(id).get("status"));
	}

	@Test
	void aSuspendedApplicationCannotAdvanceAfterTheRevocation() throws Exception {
		UUID id = application(student1, job1, "IN_REVIEW");
		as(universityA, post("/api/v1/affiliations/" + affiliation1 + "/revoke").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Trancou a matrícula.\"}")).andExpect(status().isOk());

		as(company, patch("/api/v1/applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"APPROVED\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_SUSPENDED"));
		assertEquals("SUSPENDED", row(id).get("status"));
	}

	@Test
	void theDatabaseRequiresSuspensionDataForSuspendedApplications() {
		UUID id = application(student1, job1, "SUBMITTED");
		entityManager.flush();

		assertThrows(DataIntegrityViolationException.class,
				() -> jdbcTemplate.update("UPDATE applications SET status = 'SUSPENDED' WHERE id = ?", id));
	}

	@Test
	void theDatabaseOnlyAcceptsInProgressStatusesAsSuspendedFrom() {
		UUID id = application(student1, job1, "SUBMITTED");
		entityManager.flush();

		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
				UPDATE applications SET status = 'SUSPENDED', suspended_from_status = 'APPROVED',
				  suspension_reason = 'ACADEMIC_AFFILIATION_REVOKED', suspended_at = now() WHERE id = ?
				""", id));
	}

	@Test
	void theDatabaseOnlyAcceptsKnownSuspensionReasonCodes() {
		UUID id = application(student1, job1, "SUBMITTED");
		entityManager.flush();

		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
				UPDATE applications SET status = 'SUSPENDED', suspended_from_status = 'SUBMITTED',
				  suspension_reason = 'porque sim', suspended_at = now() WHERE id = ?
				""", id));
	}

	@Test
	void applicationEventsAreDeliveredSynchronously() {
		// Um multicaster com executor entregaria os eventos em outra thread e fora da transação: a suspensão
		// deixaria de ser atômica com a revogação. Este teste falha se alguém configurar isso.
		assertNull(ReflectionTestUtils.getField(eventMulticaster, "taskExecutor"));
	}

	private Map<String, Object> row(UUID id) {
		entityManager.flush();
		return jdbcTemplate.queryForMap("""
				SELECT status, status_reason, suspended_from_status, suspension_reason, suspended_at, updated_at
				FROM applications WHERE id = ?""", id);
	}

	private UUID application(User owner, String job, String applicationStatus) {
		Timestamp when = Timestamp.from(OLD);
		return jdbcTemplate.queryForObject("""
				INSERT INTO applications (student_id, job_id, resume_type, status, resume_snapshot, created_at, updated_at)
				VALUES (?, ?, 'PLATFORM', ?, '{}'::jsonb, ?, ?) RETURNING id
				""", UUID.class, studentProfileId(owner), UUID.fromString(job), applicationStatus, when, when);
	}

	private UUID studentProfileId(User owner) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class, owner.getId());
	}

	private UUID createUniversity(User account, String name) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, ?, 'APPROVED') RETURNING id",
				UUID.class, account.getId(), name);
	}

	private UUID affiliate(User owner, UUID university) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, 'APPROVED')
				RETURNING id""", UUID.class, studentProfileId(owner), university);
	}

	private User createStudent(String name) throws Exception {
		User owner = createUser(UserRole.STUDENT);
		as(owner, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"%s\"}".formatted(name))).andExpect(status().isOk());
		return owner;
	}

	private String publishJob(User owner, String title) throws Exception {
		UUID java = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = 'java'", UUID.class);
		String response = as(owner, post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "%s", "description": "Descrição da vaga.", "area": "BACKEND", "location": null,
				 "workModel": "REMOTE", "level": "INTERNSHIP", "requiredSkillIds": ["%s"], "desiredSkillIds": []}
				""".formatted(title, java)))
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
