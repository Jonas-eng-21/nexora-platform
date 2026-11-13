package br.com.nexora.administration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import br.com.nexora.administration.service.AuditLogService;
import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;

/**
 * Prova a atomicidade entre a mudança de status e a auditoria. Fica FORA de {@code @Transactional} de propósito:
 * dentro da transação de teste, que sempre faz rollback, não daria para saber se o desfazimento veio do serviço.
 * Aqui cada requisição tem a própria transação e os dados são gravados de verdade — por isso a limpeza explícita.
 */
@SpringBootTest
class AdminApplicationStatusRollbackTests {

	private static final String PREFIX = "rollback31-";
	private static final Instant OLD = Instant.parse("2026-11-01T12:00:00Z");

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

	private MockMvc mockMvc;
	private User admin;
	private UUID applicationId;

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		admin = createUser(UserRole.ADMIN);
		User company = createUser(UserRole.COMPANY);
		as(company, put("/api/v1/companies/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Empresa Rollback\"}")).andExpect(status().isOk());
		UUID java = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = 'java'", UUID.class);
		String job = JsonPath.read(as(company, post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "Vaga Rollback", "description": "Descrição.", "area": "BACKEND", "location": null,
				 "workModel": "REMOTE", "level": "INTERNSHIP", "requiredSkillIds": ["%s"], "desiredSkillIds": []}
				""".formatted(java))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
				"$.id");

		User universityAccount = createUser(UserRole.UNIVERSITY);
		UUID university = jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'UFAPE', 'APPROVED') RETURNING id",
				UUID.class, universityAccount.getId());
		User student = createUser(UserRole.STUDENT);
		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Ana Rollback\"}")).andExpect(status().isOk());
		UUID studentId = jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class,
				student.getId());
		jdbcTemplate.update("INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, 'APPROVED')",
				studentId, university);
		Timestamp when = Timestamp.from(OLD);
		applicationId = jdbcTemplate.queryForObject("""
				INSERT INTO applications (student_id, job_id, resume_type, status, resume_snapshot, created_at, updated_at)
				VALUES (?, ?, 'PLATFORM', 'SUBMITTED', '{}'::jsonb, ?, ?) RETURNING id
				""", UUID.class, studentId, UUID.fromString(job), when, when);
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE admin_id IN (SELECT id FROM users WHERE email LIKE ?)",
				PREFIX + "%");
		jdbcTemplate.update("""
				DELETE FROM applications WHERE student_id IN
				  (SELECT id FROM students WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?))""", PREFIX + "%");
		jdbcTemplate.update("""
				DELETE FROM university_affiliations WHERE student_id IN
				  (SELECT id FROM students WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?))""", PREFIX + "%");
		jdbcTemplate.update("""
				DELETE FROM job_skills WHERE job_id IN (SELECT j.id FROM jobs j JOIN companies c ON c.id = j.company_id
				  WHERE c.user_id IN (SELECT id FROM users WHERE email LIKE ?))""", PREFIX + "%");
		jdbcTemplate.update("""
				DELETE FROM jobs WHERE company_id IN
				  (SELECT id FROM companies WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?))""", PREFIX + "%");
		jdbcTemplate.update("DELETE FROM students WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
				PREFIX + "%");
		jdbcTemplate.update("DELETE FROM companies WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
				PREFIX + "%");
		jdbcTemplate.update("DELETE FROM universities WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
				PREFIX + "%");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", PREFIX + "%");
	}

	@Test
	void aFailingAuditRecordUndoesTheStatusChange() throws Exception {
		makeAuditRecordingFail();

		as(admin, patch("/api/v1/admin/applications/" + applicationId + "/status")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"REJECTED\", \"reason\": \"Perfil não atende.\"}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

		// Nada ficou gravado: a mudança foi desfeita junto com a auditoria que falhou.
		assertEquals("SUBMITTED", jdbcTemplate.queryForObject(
				"SELECT status FROM applications WHERE id = ?", String.class, applicationId));
		assertNull(jdbcTemplate.queryForObject(
				"SELECT status_reason FROM applications WHERE id = ?", String.class, applicationId));
		assertEquals(OLD, jdbcTemplate.queryForObject(
				"SELECT updated_at FROM applications WHERE id = ?", Timestamp.class, applicationId).toInstant());
		assertEquals(0L, auditRows());
	}

	@Test
	void theSameRequestSucceedsAndIsAuditedWhenTheAuditWorks() throws Exception {
		as(admin, patch("/api/v1/admin/applications/" + applicationId + "/status")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"REJECTED\", \"reason\": \"Perfil não atende.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		assertEquals("REJECTED", jdbcTemplate.queryForObject(
				"SELECT status FROM applications WHERE id = ?", String.class, applicationId));
		assertEquals(1L, auditRows());
		assertEquals("SUBMITTED", jdbcTemplate.queryForObject(
				"SELECT previous_value FROM admin_audit_logs WHERE admin_id = ?", String.class, admin.getId()));
	}

	/**
	 * O stub é preparado dentro de uma transação porque {@code record} exige uma (MANDATORY) e a chamada de
	 * preparo passa pelo proxy transacional antes de chegar ao Mockito. O spy volta ao normal sozinho após o teste.
	 */
	private void makeAuditRecordingFail() {
		new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> doThrow(
				new IllegalStateException("auditoria indisponível")).when(auditLogService)
				.record(any(), any(), any(), any(), any(), any(), any()));
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
}
