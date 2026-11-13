package br.com.nexora.applications;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

@SpringBootTest
@Transactional
class ApplicationStatusUpdateTests {

	private static final List<String> STATUSES = List.of("SUBMITTED", "IN_REVIEW", "APPROVED", "REJECTED", "SUSPENDED");
	private static final List<String> ALLOWED = List.of("SUBMITTED>IN_REVIEW", "SUBMITTED>APPROVED",
			"SUBMITTED>REJECTED", "IN_REVIEW>APPROVED", "IN_REVIEW>REJECTED");
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

	private MockMvc mockMvc;
	private User company;
	private UUID universityId;
	private User universityAccount;
	private User student;
	private String jobId;
	private UUID applicationId;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		company = createCompany("Tech Garanhuns");
		universityAccount = createUser(UserRole.UNIVERSITY);
		universityId = jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'UFAPE', 'APPROVED') RETURNING id",
				UUID.class, universityAccount.getId());
		student = createStudentWithProfile("Ana Souza");
		jdbcTemplate.update("INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, 'APPROVED')",
				studentProfileId(student), universityId);
		jobId = publishJob(company, "Estágio Backend Java");
		applicationId = insertApplication(student, jobId, "SUBMITTED");
	}

	@Test
	void owningCompanyMovesAnApplicationForwardAndTheChangeIsStored() throws Exception {
		as(company, change(applicationId, "IN_REVIEW", null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(applicationId.toString()))
				.andExpect(jsonPath("$.previousStatus").value("SUBMITTED"))
				.andExpect(jsonPath("$.status").value("IN_REVIEW"))
				.andExpect(jsonPath("$.reason").value(nullValue()))
				.andExpect(jsonPath("$.updatedAt").isNotEmpty());
		assertEquals("IN_REVIEW", statusInDb());

		as(company, change(applicationId, "APPROVED", "Ótima entrevista técnica."))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.previousStatus").value("IN_REVIEW"))
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.reason").value("Ótima entrevista técnica."));
		assertEquals("APPROVED", statusInDb());
		assertEquals("Ótima entrevista técnica.", reasonInDb());
		// A lista da empresa já enxerga o novo status.
		as(company, get("/api/v1/jobs/" + jobId + "/applications"))
				.andExpect(jsonPath("$.content[0].status").value("APPROVED"));
		// Fora de qualquer dado do currículo: a resposta traz só a decisão.
		as(company, change(insertOther("Bruno Lima"), "IN_REVIEW", null))
				.andExpect(jsonPath("$.length()").value(5))
				.andExpect(content().string(not(containsString("snapshot"))))
				.andExpect(content().string(not(containsString("storageKey"))));
	}

	@Test
	void everyAllowedTransitionWorks() throws Exception {
		for (String transition : ALLOWED) {
			String[] parts = transition.split(">");
			setStatus(parts[0]);
			as(company, change(applicationId, parts[1], "Motivo da decisão."))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value(parts[1]));
			assertEquals(parts[1], statusInDb(), transition);
		}
	}

	@Test
	void everyOtherTransitionIsRejectedAndNothingChanges() throws Exception {
		for (String from : STATUSES) {
			if (from.equals("SUSPENDED")) {
				continue;
			}
			for (String to : STATUSES) {
				if (ALLOWED.contains(from + ">" + to)) {
					continue;
				}
				setStatus(from);
				as(company, change(applicationId, to, "Motivo."))
						.andExpect(status().isConflict())
						.andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS_TRANSITION"));
				assertEquals(from, statusInDb(), from + " -> " + to);
			}
		}
	}

	@Test
	void finalStatesCannotBeChangedNotEvenToThemselves() throws Exception {
		for (String finalState : List.of("APPROVED", "REJECTED")) {
			for (String to : STATUSES) {
				setStatus(finalState);
				as(company, change(applicationId, to, "Tentando alterar."))
						.andExpect(status().isConflict())
						.andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS_TRANSITION"));
				assertEquals(finalState, statusInDb());
			}
		}
	}

	@Test
	void companyCannotAssignSuspended() throws Exception {
		for (String from : List.of("SUBMITTED", "IN_REVIEW")) {
			setStatus(from);
			as(company, change(applicationId, "SUSPENDED", "Suspender manualmente."))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS_TRANSITION"));
			assertEquals(from, statusInDb());
		}
	}

	@Test
	void aSuspendedApplicationCannotAdvance() throws Exception {
		setStatus("SUSPENDED");
		for (String to : STATUSES) {
			as(company, change(applicationId, to, "Motivo."))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("APPLICATION_SUSPENDED"));
		}
		assertEquals("SUSPENDED", statusInDb());
	}

	@Test
	void aStudentWithoutAValidAcademicLinkCannotAdvance() throws Exception {
		// Revogado.
		entityManager.flush();
		jdbcTemplate.update("UPDATE university_affiliations SET status = 'REVOKED' WHERE student_id = ?",
				studentProfileId(student));
		entityManager.clear();
		for (String to : List.of("IN_REVIEW", "APPROVED", "REJECTED")) {
			as(company, change(applicationId, to, "Motivo."))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("STUDENT_NOT_VALIDATED"));
		}
		assertEquals("SUBMITTED", statusInDb());

		// Universidade suspensa: o vínculo aprovado deixa de valer.
		entityManager.flush();
		jdbcTemplate.update("UPDATE university_affiliations SET status = 'APPROVED' WHERE student_id = ?",
				studentProfileId(student));
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", universityAccount.getId());
		entityManager.clear();
		as(company, change(applicationId, "IN_REVIEW", null))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("STUDENT_NOT_VALIDATED"));
		assertEquals("SUBMITTED", statusInDb());

		// Revalidado: volta a poder avançar, sem nenhuma ação extra.
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", universityAccount.getId());
		entityManager.clear();
		as(company, change(applicationId, "IN_REVIEW", null)).andExpect(status().isOk());
	}

	@Test
	void rejectionRequiresANonBlankReason() throws Exception {
		for (String from : List.of("SUBMITTED", "IN_REVIEW")) {
			for (String reason : new String[] { null, "", "   ", "\t\n " }) {
				setStatus(from);
				as(company, change(applicationId, "REJECTED", reason))
						.andExpect(status().isBadRequest())
						.andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"));
				assertEquals(from, statusInDb());
			}
		}
		setStatus("IN_REVIEW");
		as(company, change(applicationId, "REJECTED", "  Perfil não atende aos requisitos da oportunidade.  "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reason").value("Perfil não atende aos requisitos da oportunidade."));
		assertEquals("REJECTED", statusInDb());
	}

	@Test
	void reasonIsOptionalForReviewAndApprovalAndBlankBecomesNull() throws Exception {
		as(company, change(applicationId, "IN_REVIEW", "   ")).andExpect(status().isOk())
				.andExpect(jsonPath("$.reason").value(nullValue()));
		assertNull(reasonInDb());
		as(company, change(applicationId, "APPROVED", null)).andExpect(status().isOk())
				.andExpect(jsonPath("$.reason").value(nullValue()));
	}

	@Test
	void reasonIsLimitedToFiveHundredCharacters() throws Exception {
		as(company, change(applicationId, "REJECTED", "x".repeat(501)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("reason"));
		as(company, change(applicationId, "IN_REVIEW", "y".repeat(501))).andExpect(status().isBadRequest());
		assertEquals("SUBMITTED", statusInDb());

		as(company, change(applicationId, "REJECTED", "z".repeat(500)))
				.andExpect(status().isOk());
		assertEquals(500, reasonInDb().length());
	}

	@Test
	void statusIsRequiredAndMustBeKnown() throws Exception {
		as(company, patch("/api/v1/applications/" + applicationId + "/status")
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("status"));
		as(company, patch("/api/v1/applications/" + applicationId + "/status")
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ARCHIVED\"}"))
				.andExpect(status().isBadRequest());
		assertEquals("SUBMITTED", statusInDb());
	}

	@Test
	void updatedAtMovesForwardWithTheChange() throws Exception {
		Instant before = updatedAtInDb();
		assertEquals(OLD, before);

		String response = as(company, change(applicationId, "IN_REVIEW", null)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		Instant fromResponse = Instant.parse(JsonPath.read(response, "$.updatedAt"));
		assertTrue(fromResponse.isAfter(before), "updatedAt da resposta");
		assertTrue(updatedAtInDb().isAfter(before), "updated_at gravado");
	}

	@Test
	void anotherCompanyAndUnknownApplicationsAreNotFound() throws Exception {
		as(createCompany("Outra Empresa"), change(applicationId, "IN_REVIEW", null))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		as(company, change(UUID.randomUUID(), "IN_REVIEW", null))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		as(createUser(UserRole.COMPANY), change(applicationId, "IN_REVIEW", null))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		assertEquals("SUBMITTED", statusInDb());
	}

	@Test
	void clientSuppliedOwnershipIsIgnored() throws Exception {
		User intruder = createCompany("Outra Empresa");
		as(intruder, patch("/api/v1/applications/" + applicationId + "/status")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"IN_REVIEW\", \"companyId\": \"%s\", \"companyUserId\": \"%s\"}"
						.formatted(UUID.randomUUID(), company.getId())))
				.andExpect(status().isNotFound());
		assertEquals("SUBMITTED", statusInDb());
	}

	@Test
	void onlyCompaniesUseTheCompanyRouteAndSuspendedOnesAreBlocked() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			as(createUser(role), change(applicationId, "IN_REVIEW", null)).andExpect(status().isForbidden());
		}
		// Nem o estudante dono da candidatura decide sobre ela.
		as(student, change(applicationId, "APPROVED", null)).andExpect(status().isForbidden());
		mockMvc.perform(change(applicationId, "IN_REVIEW", null)).andExpect(status().isUnauthorized());

		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", company.getId());
		entityManager.clear();
		as(company, change(applicationId, "IN_REVIEW", null))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
		assertEquals("SUBMITTED", statusInDb());
	}

	@Test
	void companyChangesLeaveNoAdminAuditTrail() throws Exception {
		as(company, change(applicationId, "IN_REVIEW", null)).andExpect(status().isOk());
		assertEquals(0L, auditCount());
	}

	@Test
	void adminMakesTheSameOrdinaryTransitionsAndEachOneIsAudited() throws Exception {
		User admin = createUser(UserRole.ADMIN);

		as(admin, adminChange(applicationId, "IN_REVIEW", null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.previousStatus").value("SUBMITTED"))
				.andExpect(jsonPath("$.status").value("IN_REVIEW"));
		as(admin, adminChange(applicationId, "REJECTED", "Documentação inconsistente."))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.reason").value("Documentação inconsistente."));
		assertEquals("REJECTED", statusInDb());

		entityManager.flush();
		List<Map<String, Object>> trail = jdbcTemplate.queryForList(
				"SELECT admin_id, action, target_type, target_id, reason, previous_value, new_value, created_at "
						+ "FROM admin_audit_logs ORDER BY created_at, new_value");
		assertEquals(2, trail.size());
		Map<String, Object> toReview = trail.stream().filter(row -> "IN_REVIEW".equals(row.get("new_value")))
				.findFirst().orElseThrow();
		assertEquals(admin.getId(), toReview.get("admin_id"));
		assertEquals("APPLICATION_STATUS_CHANGED", toReview.get("action"));
		assertEquals("APPLICATION", toReview.get("target_type"));
		assertEquals(applicationId, toReview.get("target_id"));
		assertEquals("SUBMITTED", toReview.get("previous_value"));
		assertNull(toReview.get("reason"));
		assertTrue(toReview.get("created_at") != null);
		Map<String, Object> rejected = trail.stream().filter(row -> "REJECTED".equals(row.get("new_value")))
				.findFirst().orElseThrow();
		assertEquals("IN_REVIEW", rejected.get("previous_value"));
		assertEquals("Documentação inconsistente.", rejected.get("reason"));

		// O endpoint de auditoria existente já mostra o status anterior e o novo.
		as(admin, get("/api/v1/admin/audit-logs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.newValue == 'REJECTED')].previousValue").value("IN_REVIEW"))
				.andExpect(jsonPath("$.content[?(@.newValue == 'REJECTED')].targetType").value("APPLICATION"));
	}

	@Test
	void adminRespectsFinalStatesSuspensionReasonAndEligibility() throws Exception {
		User admin = createUser(UserRole.ADMIN);
		setStatus("APPROVED");
		as(admin, adminChange(applicationId, "REJECTED", "Revisão administrativa."))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS_TRANSITION"));
		setStatus("REJECTED");
		as(admin, adminChange(applicationId, "APPROVED", "Revisão administrativa."))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS_TRANSITION"));
		setStatus("SUSPENDED");
		as(admin, adminChange(applicationId, "IN_REVIEW", null))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_SUSPENDED"));
		setStatus("SUBMITTED");
		as(admin, adminChange(applicationId, "SUSPENDED", "Suspender."))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS_TRANSITION"));
		as(admin, adminChange(applicationId, "REJECTED", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"));
		as(admin, adminChange(applicationId, "APPROVED", "x".repeat(501))).andExpect(status().isBadRequest());

		entityManager.flush();
		jdbcTemplate.update("UPDATE university_affiliations SET status = 'REVOKED' WHERE student_id = ?",
				studentProfileId(student));
		entityManager.clear();
		as(admin, adminChange(applicationId, "IN_REVIEW", null))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("STUDENT_NOT_VALIDATED"));

		// Nenhuma tentativa que falhou deixa rastro de sucesso nem altera a candidatura.
		assertEquals("SUBMITTED", statusInDb());
		assertEquals(0L, auditCount());
	}

	@Test
	void adminRouteIsRestrictedToAdminsAndUnknownApplicationsAreNotFound() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.COMPANY, UserRole.UNIVERSITY }) {
			as(createUser(role), adminChange(applicationId, "IN_REVIEW", null)).andExpect(status().isForbidden());
		}
		as(company, adminChange(applicationId, "IN_REVIEW", null)).andExpect(status().isForbidden());
		mockMvc.perform(adminChange(applicationId, "IN_REVIEW", null)).andExpect(status().isUnauthorized());

		as(createUser(UserRole.ADMIN), adminChange(UUID.randomUUID(), "IN_REVIEW", null))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		assertEquals("SUBMITTED", statusInDb());
		assertEquals(0L, auditCount());
	}

	private static AbstractMockHttpServletRequestBuilder<?> change(UUID id, String newStatus, String reason) {
		return patch("/api/v1/applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
				.content(body(newStatus, reason));
	}

	private static AbstractMockHttpServletRequestBuilder<?> adminChange(UUID id, String newStatus, String reason) {
		return patch("/api/v1/admin/applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
				.content(body(newStatus, reason));
	}

	private static String body(String newStatus, String reason) {
		String reasonJson = reason == null ? "" : ", \"reason\": \"%s\"".formatted(
				reason.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t"));
		return "{\"status\": \"%s\"%s}".formatted(newStatus, reasonJson);
	}

	private void setStatus(String newStatus) {
		entityManager.flush();
		// Uma candidatura SUSPENDED sempre traz os dados da suspensão (restrição do banco).
		jdbcTemplate.update("""
				UPDATE applications SET status = ?, status_reason = NULL,
				  suspended_from_status = CASE WHEN ?::text = 'SUSPENDED' THEN 'SUBMITTED' END,
				  suspension_reason = CASE WHEN ?::text = 'SUSPENDED' THEN 'ACADEMIC_AFFILIATION_REVOKED' END,
				  suspended_at = CASE WHEN ?::text = 'SUSPENDED' THEN now() END
				WHERE id = ?""", newStatus, newStatus, newStatus, newStatus, applicationId);
		entityManager.clear();
	}

	private String statusInDb() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT status FROM applications WHERE id = ?", String.class,
				applicationId);
	}

	private String reasonInDb() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT status_reason FROM applications WHERE id = ?", String.class,
				applicationId);
	}

	private Instant updatedAtInDb() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT updated_at FROM applications WHERE id = ?", Timestamp.class,
				applicationId).toInstant();
	}

	private long auditCount() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_logs", Long.class);
	}

	private UUID insertOther(String name) throws Exception {
		User other = createStudentWithProfile(name);
		jdbcTemplate.update("INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, 'APPROVED')",
				studentProfileId(other), universityId);
		return insertApplication(other, jobId, "SUBMITTED");
	}

	private UUID insertApplication(User owner, String job, String applicationStatus) {
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

	private User createStudentWithProfile(String name) throws Exception {
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
