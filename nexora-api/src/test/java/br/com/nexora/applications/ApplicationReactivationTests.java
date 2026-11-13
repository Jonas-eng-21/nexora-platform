package br.com.nexora.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * Regras da reativação explícita. Roda em transação de teste (rollback no fim); a atomicidade com a auditoria e a
 * concorrência estão em {@code ApplicationReactivationTransactionTests}, que não usa transação de teste.
 */
@SpringBootTest
@Transactional
class ApplicationReactivationTests {

	private static final Instant OLD = Instant.parse("2026-11-01T12:00:00Z");
	private static final String SNAPSHOT = "{\"profile\": {\"fullName\": \"Ana Souza\"}, \"skills\": []}";

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
	private User admin;
	private User company;
	private User universityAccount;
	private UUID universityId;
	private User student;
	private UUID affiliationId;
	private String jobId;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		admin = createUser(UserRole.ADMIN);
		company = createCompany("Tech Garanhuns");
		universityAccount = createUser(UserRole.UNIVERSITY);
		universityId = jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'UFAPE', 'APPROVED') RETURNING id",
				UUID.class, universityAccount.getId());
		student = createStudent("Ana Souza");
		affiliationId = jdbcTemplate.queryForObject("""
				INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, 'APPROVED')
				RETURNING id""", UUID.class, studentProfileId(student), universityId);
		jobId = publishJob(company, "Estágio Backend Java");
	}

	@Test
	void owningCompanyReactivatesAndTheExactPreviousStatusIsRestored() throws Exception {
		for (String previous : List.of("SUBMITTED", "IN_REVIEW")) {
			String job = publishJob(company, "Vaga " + previous);
			UUID id = suspended(student, job, previous);
			jdbcTemplate.update("UPDATE applications SET status_reason = 'Entrevista agendada.' WHERE id = ?", id);

			as(company, reactivate(id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(id.toString()))
					.andExpect(jsonPath("$.previousStatus").value("SUSPENDED"))
					.andExpect(jsonPath("$.status").value(previous))
					.andExpect(jsonPath("$.updatedAt").isNotEmpty())
					.andExpect(jsonPath("$.length()").value(4));

			Map<String, Object> row = row(id);
			assertEquals(previous, row.get("status"));
			// Os três metadados da suspensão saem juntos; a última decisão ordinária e o currículo ficam.
			assertNull(row.get("suspended_from_status"));
			assertNull(row.get("suspension_reason"));
			assertNull(row.get("suspended_at"));
			assertEquals("Entrevista agendada.", row.get("status_reason"));
			assertTrue(((Timestamp) row.get("updated_at")).toInstant().isAfter(OLD), "updated_at");
			// jsonb reordena as chaves: compara o conteúdo, não o texto.
			Object expectedSnapshot = JsonPath.parse(SNAPSHOT).json();
			Object actualSnapshot = JsonPath.parse((String) row.get("snapshot")).json();
			assertEquals(expectedSnapshot, actualSnapshot);
		}
	}

	@Test
	void aPdfApplicationKeepsItsFileDataAfterBeingReactivated() throws Exception {
		UUID id = suspendedPdf(student, jobId, "IN_REVIEW");
		Map<String, Object> before = fileColumns(id);

		as(company, reactivate(id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));

		assertEquals(before, fileColumns(id));
		assertEquals("IN_REVIEW", row(id).get("status"));
	}

	@Test
	void aReactivatedApplicationAdvancesAgainAndANewRevocationSuspendsItAgain() throws Exception {
		UUID id = suspended(student, jobId, "IN_REVIEW");
		as(company, reactivate(id)).andExpect(status().isOk());

		// Voltou ao andamento: a empresa já pode decidir...
		as(company, patch("/api/v1/applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"APPROVED\", \"reason\": \"Aprovada.\"}")).andExpect(status().isOk());
		UUID another = suspended(student, publishJob(company, "Outra vaga"), "SUBMITTED");
		as(company, reactivate(another)).andExpect(status().isOk());

		// ...e uma revogação posterior suspende de novo, com os dados novos (a aprovada, final, fica como está).
		as(universityAccount, post("/api/v1/affiliations/" + affiliationId + "/revoke")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Trancou a matrícula.\"}"))
				.andExpect(status().isOk());
		assertEquals("APPROVED", row(id).get("status"));
		Map<String, Object> again = row(another);
		assertEquals("SUSPENDED", again.get("status"));
		assertEquals("SUBMITTED", again.get("suspended_from_status"));
		assertEquals("ACADEMIC_AFFILIATION_REVOKED", again.get("suspension_reason"));
	}

	@Test
	void companyRouteAuthorization() throws Exception {
		UUID id = suspended(student, jobId, "SUBMITTED");

		as(createCompany("Outra Empresa"), reactivate(id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		as(company, reactivate(UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		as(createUser(UserRole.COMPANY), reactivate(id)).andExpect(status().isNotFound());
		for (User user : new User[] { student, universityAccount, admin }) {
			as(user, reactivate(id)).andExpect(status().isForbidden());
		}
		mockMvc.perform(reactivate(id)).andExpect(status().isUnauthorized());
		assertEquals("SUSPENDED", row(id).get("status"));

		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", company.getId());
		entityManager.clear();
		as(company, reactivate(id))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
		assertEquals("SUSPENDED", row(id).get("status"));
	}

	@Test
	void adminReactivatesAndIsAuditedWhileCompanyReactivationsAreNot() throws Exception {
		UUID byCompany = suspended(student, jobId, "SUBMITTED");
		as(company, reactivate(byCompany)).andExpect(status().isOk());
		assertEquals(0L, auditCount());

		UUID byAdmin = suspended(student, publishJob(company, "Vaga do admin"), "IN_REVIEW");
		as(admin, adminReactivate(byAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.previousStatus").value("SUSPENDED"))
				.andExpect(jsonPath("$.status").value("IN_REVIEW"));

		entityManager.flush();
		Map<String, Object> audit = jdbcTemplate.queryForMap("""
				SELECT admin_id, action, target_type, target_id, reason, previous_value, new_value, created_at
				FROM admin_audit_logs""");
		assertEquals(admin.getId(), audit.get("admin_id"));
		assertEquals("APPLICATION_REACTIVATED", audit.get("action"));
		assertEquals("APPLICATION", audit.get("target_type"));
		assertEquals(byAdmin, audit.get("target_id"));
		assertEquals("SUSPENDED", audit.get("previous_value"));
		assertEquals("IN_REVIEW", audit.get("new_value"));
		assertNotNull(audit.get("created_at"));
	}

	@Test
	void adminRouteIsRestrictedAndFailedAttemptsLeaveNoAudit() throws Exception {
		UUID id = suspended(student, jobId, "SUBMITTED");
		for (User user : new User[] { student, universityAccount, company }) {
			as(user, adminReactivate(id)).andExpect(status().isForbidden());
		}
		mockMvc.perform(adminReactivate(id)).andExpect(status().isUnauthorized());
		as(admin, adminReactivate(UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));

		setStatus(id, "IN_REVIEW");
		as(admin, adminReactivate(id)).andExpect(status().isConflict());
		assertEquals(0L, auditCount());
	}

	@Test
	void onlySuspendedApplicationsCanBeReactivated() throws Exception {
		UUID id = suspended(student, jobId, "SUBMITTED");
		for (String notSuspended : List.of("SUBMITTED", "IN_REVIEW", "APPROVED", "REJECTED")) {
			setStatus(id, notSuspended);
			as(company, reactivate(id))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("APPLICATION_NOT_SUSPENDED"));
			as(admin, adminReactivate(id))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("APPLICATION_NOT_SUSPENDED"));
			assertEquals(notSuspended, row(id).get("status"));
		}
	}

	@Test
	void anInvalidOrMissingPreviousStatusIsRefusedAndNothingChanges() throws Exception {
		UUID id = suspended(student, jobId, "SUBMITTED");
		entityManager.flush();
		// No banco consistente a V18 impede esses estados; a checagem do serviço é uma defesa em profundidade.
		// O DDL do PostgreSQL é transacional: as restrições voltam no rollback do teste.
		jdbcTemplate.execute("ALTER TABLE applications DROP CONSTRAINT ck_applications_suspended_has_metadata");
		jdbcTemplate.execute("ALTER TABLE applications DROP CONSTRAINT ck_applications_suspended_from_status");

		for (String invalid : new String[] { null, "APPROVED", "REJECTED", "SUSPENDED" }) {
			entityManager.flush();
			jdbcTemplate.update(
					"UPDATE applications SET status = 'SUSPENDED', suspended_from_status = ?::varchar WHERE id = ?",
					invalid, id);
			entityManager.clear();
			as(company, reactivate(id))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("INVALID_SUSPENSION_STATE"));
			Map<String, Object> row = row(id);
			assertEquals("SUSPENDED", row.get("status"));
			assertEquals(invalid, row.get("suspended_from_status"));
			assertNotNull(row.get("suspension_reason"));
		}
		assertEquals(0L, auditCount());
	}

	@Test
	void aStudentWithoutAnApprovedLinkCannotBeReactivated() throws Exception {
		UUID id = suspended(student, jobId, "IN_REVIEW");
		for (String linkStatus : List.of("REVOKED", "PENDING", "REJECTED")) {
			entityManager.flush();
			jdbcTemplate.update("UPDATE university_affiliations SET status = ? WHERE id = ?", linkStatus,
					affiliationId);
			entityManager.clear();
			as(company, reactivate(id))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("STUDENT_NOT_VALIDATED"));
			as(admin, adminReactivate(id)).andExpect(status().isForbidden());
		}
		entityManager.flush();
		jdbcTemplate.update("DELETE FROM university_affiliations WHERE id = ?", affiliationId);
		entityManager.clear();
		as(company, reactivate(id)).andExpect(status().isForbidden());

		Map<String, Object> row = row(id);
		assertEquals("SUSPENDED", row.get("status"));
		assertEquals("IN_REVIEW", row.get("suspended_from_status"));
		assertEquals(0L, auditCount());
	}

	@Test
	void aSuspendedUniversityBlocksTheReactivation() throws Exception {
		UUID id = suspended(student, jobId, "SUBMITTED");
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", universityAccount.getId());
		entityManager.clear();

		as(company, reactivate(id))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("STUDENT_NOT_VALIDATED"));
		assertEquals("SUSPENDED", row(id).get("status"));

		// Regularizada a universidade, a reativação (explícita) volta a ser possível.
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", universityAccount.getId());
		entityManager.clear();
		as(company, reactivate(id)).andExpect(status().isOk());
	}

	@Test
	void aStudentWhoseAccountIsNotActiveCannotBeReactivated() throws Exception {
		UUID id = suspended(student, jobId, "SUBMITTED");
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", student.getId());
		entityManager.clear();

		as(company, reactivate(id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("STUDENT_ACCOUNT_NOT_ACTIVE"));
		as(admin, adminReactivate(id)).andExpect(status().isConflict());
		assertEquals("SUSPENDED", row(id).get("status"));
		assertEquals(0L, auditCount());
	}

	@Test
	void aClosedDeletedOrInactiveCompanyJobBlocksTheReactivation() throws Exception {
		UUID id = suspended(student, jobId, "IN_REVIEW");

		as(company, post("/api/v1/jobs/" + jobId + "/close")).andExpect(status().isOk());
		as(company, reactivate(id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("JOB_NOT_AVAILABLE"));
		as(company, post("/api/v1/jobs/" + jobId + "/reopen")).andExpect(status().isOk());

		as(company, delete("/api/v1/jobs/" + jobId)).andExpect(status().isNoContent());
		as(company, reactivate(id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("JOB_NOT_AVAILABLE"));
		as(admin, adminReactivate(id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("JOB_NOT_AVAILABLE"));
		assertEquals("SUSPENDED", row(id).get("status"));
		assertEquals(0L, auditCount());
	}

	@Test
	void aSuspendedCompanyMakesTheJobUnavailableForTheAdminRoute() throws Exception {
		UUID id = suspended(student, jobId, "SUBMITTED");
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", company.getId());
		entityManager.clear();

		as(admin, adminReactivate(id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("JOB_NOT_AVAILABLE"));
		assertEquals("SUSPENDED", row(id).get("status"));
		assertEquals(0L, auditCount());
	}

	private static AbstractMockHttpServletRequestBuilder<?> reactivate(UUID id) {
		return post("/api/v1/applications/" + id + "/reactivate");
	}

	private static AbstractMockHttpServletRequestBuilder<?> adminReactivate(UUID id) {
		return post("/api/v1/admin/applications/" + id + "/reactivate");
	}

	private Map<String, Object> row(UUID id) {
		entityManager.flush();
		return jdbcTemplate.queryForMap("""
				SELECT status, status_reason, suspended_from_status, suspension_reason, suspended_at, updated_at,
				       resume_snapshot::text AS snapshot
				FROM applications WHERE id = ?""", id);
	}

	private Map<String, Object> fileColumns(UUID id) {
		entityManager.flush();
		return jdbcTemplate.queryForMap("""
				SELECT resume_type, storage_key, original_filename, file_size, content_type, resume_snapshot::text AS snapshot
				FROM applications WHERE id = ?""", id);
	}

	private void setStatus(UUID id, String newStatus) {
		entityManager.flush();
		jdbcTemplate.update("""
				UPDATE applications SET status = ?,
				  suspended_from_status = CASE WHEN ?::text = 'SUSPENDED' THEN 'SUBMITTED' END,
				  suspension_reason = CASE WHEN ?::text = 'SUSPENDED' THEN 'ACADEMIC_AFFILIATION_REVOKED' END,
				  suspended_at = CASE WHEN ?::text = 'SUSPENDED' THEN now() END
				WHERE id = ?""", newStatus, newStatus, newStatus, newStatus, id);
		entityManager.clear();
	}

	private long auditCount() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_logs", Long.class);
	}

	/** Candidatura PLATFORM já suspensa, com o estado anterior informado e a data antiga. */
	private UUID suspended(User owner, String job, String from) {
		Timestamp when = Timestamp.from(OLD);
		return jdbcTemplate.queryForObject("""
				INSERT INTO applications (student_id, job_id, resume_type, status, resume_snapshot, created_at, updated_at,
				                          suspended_from_status, suspension_reason, suspended_at)
				VALUES (?, ?, 'PLATFORM', 'SUSPENDED', ?::jsonb, ?, ?, ?, 'ACADEMIC_AFFILIATION_REVOKED', ?) RETURNING id
				""", UUID.class, studentProfileId(owner), UUID.fromString(job), SNAPSHOT, when, when, from, when);
	}

	private UUID suspendedPdf(User owner, String job, String from) {
		Timestamp when = Timestamp.from(OLD);
		return jdbcTemplate.queryForObject("""
				INSERT INTO applications (student_id, job_id, resume_type, status, storage_key, original_filename,
				                          file_size, content_type, created_at, updated_at,
				                          suspended_from_status, suspension_reason, suspended_at)
				VALUES (?, ?, 'PDF', 'SUSPENDED', ?, 'curriculo.pdf', 1234, 'application/pdf', ?, ?, ?,
				        'UNIVERSITY_SUSPENDED', ?) RETURNING id
				""", UUID.class, studentProfileId(owner), UUID.fromString(job), UUID.randomUUID() + ".pdf", when, when,
				from, when);
	}

	private UUID studentProfileId(User owner) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class, owner.getId());
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
