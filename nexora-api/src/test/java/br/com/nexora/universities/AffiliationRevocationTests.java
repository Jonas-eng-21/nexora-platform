package br.com.nexora.universities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AffiliationRevocationTests {

	private static final String AFFILIATIONS = "/api/v1/affiliations";
	private static final String ADMIN = "/api/v1/admin/affiliations";
	private static final String REASON = "{\"reason\": \"Estudante desligado do curso\"}";

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
	private User university;
	private UUID universityId;
	private User student;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		university = createUser(UserRole.UNIVERSITY);
		universityId = createUniversityFor(university, "UFAPE");
		student = createStudentWithProfile("Ana Souza");
	}

	@Test
	void universityListsItsLinkedStudents() throws Exception {
		UUID approved = approvedAffiliation(student);
		requestAffiliation(createStudentWithProfile("Bruno Lima"), universityId);

		as(university, get(AFFILIATIONS + "/received").param("status", "APPROVED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(approved.toString()))
				.andExpect(jsonPath("$.content[0].student.fullName").value("Ana Souza"));
	}

	@Test
	void universityRevokesOwnAffiliationKeepingHistory() throws Exception {
		UUID affiliation = approvedAffiliation(student);

		revoke(university, AFFILIATIONS, affiliation, REASON)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVOKED"))
				.andExpect(jsonPath("$.revokedAt").isNotEmpty())
				.andExpect(jsonPath("$.revocationReason").value("Estudante desligado do curso"));

		Map<String, Object> row = affiliationRow(affiliation);
		assertThat(row.get("status")).isEqualTo("REVOKED");
		assertThat(row.get("revoked_by")).isEqualTo(university.getId());
		assertThat(row.get("revoked_at")).isNotNull();
		assertThat(row.get("revocation_reason")).isEqualTo("Estudante desligado do curso");
		// A aprovação original continua registrada: a revogação não reescreve o histórico.
		assertThat(row.get("decided_by")).isEqualTo(university.getId());
		assertThat(row.get("decided_at")).isNotNull();
		assertThat(affiliationCount()).isEqualTo(1);
		as(student, get(AFFILIATIONS + "/me"))
				.andExpect(jsonPath("$[0].status").value("REVOKED"))
				.andExpect(jsonPath("$[0].revocationReason").value("Estudante desligado do curso"))
				.andExpect(jsonPath("$[0].revokedAt").isNotEmpty());
	}

	@Test
	void revocationRequiresReason() throws Exception {
		UUID affiliation = approvedAffiliation(student);

		revoke(university, AFFILIATIONS, affiliation, "{\"reason\": \" \"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		revoke(university, AFFILIATIONS, affiliation, "{}").andExpect(status().isBadRequest());

		assertThat(affiliationRow(affiliation).get("status")).isEqualTo("APPROVED");
	}

	@Test
	void onlyTheOwningUniversityCanRevoke() throws Exception {
		UUID affiliation = approvedAffiliation(student);
		User intruder = createUser(UserRole.UNIVERSITY);
		createUniversityFor(intruder, "Intrusa");

		revoke(intruder, AFFILIATIONS, affiliation, REASON)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("AFFILIATION_NOT_FOUND"));
		for (User user : new User[] { student, createUser(UserRole.COMPANY) }) {
			revoke(user, AFFILIATIONS, affiliation, REASON).andExpect(status().isForbidden());
			revoke(user, ADMIN, affiliation, REASON).andExpect(status().isForbidden());
		}
		revoke(intruder, ADMIN, affiliation, REASON).andExpect(status().isForbidden());
		mockMvc.perform(post(AFFILIATIONS + "/" + affiliation + "/revoke")
				.contentType(MediaType.APPLICATION_JSON).content(REASON)).andExpect(status().isUnauthorized());

		assertThat(affiliationRow(affiliation).get("status")).isEqualTo("APPROVED");
		assertThat(affiliationRow(affiliation).get("revoked_by")).isNull();
	}

	@Test
	void onlyApprovedAffiliationsCanBeRevoked() throws Exception {
		UUID pending = requestAffiliation(student, universityId);
		revoke(university, AFFILIATIONS, pending, REASON)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AFFILIATION_NOT_APPROVED"));

		decide(pending, "reject", REASON);
		revoke(university, AFFILIATIONS, pending, REASON).andExpect(status().isConflict());

		UUID approved = approvedAffiliation(student);
		revoke(university, AFFILIATIONS, approved, REASON).andExpect(status().isOk());
		revoke(university, AFFILIATIONS, approved, REASON)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AFFILIATION_NOT_APPROVED"));
		revoke(university, AFFILIATIONS, UUID.randomUUID(), REASON).andExpect(status().isNotFound());
	}

	@Test
	void studentCanBeLinkedAgainAfterRevocation() throws Exception {
		UUID first = approvedAffiliation(student);
		revoke(university, AFFILIATIONS, first, REASON).andExpect(status().isOk());

		UUID second = approvedAffiliation(student);

		assertThat(affiliationRow(first).get("status")).isEqualTo("REVOKED");
		assertThat(affiliationRow(second).get("status")).isEqualTo("APPROVED");
		as(student, get(AFFILIATIONS + "/me"))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].status").value("APPROVED"))
				.andExpect(jsonPath("$[1].status").value("REVOKED"));
	}

	@Test
	void adminRevokesAnyAffiliationWithAudit() throws Exception {
		User admin = createUser(UserRole.ADMIN);
		UUID affiliation = approvedAffiliation(student);

		revoke(admin, ADMIN, affiliation, "{}").andExpect(status().isBadRequest());
		revoke(admin, ADMIN, affiliation, REASON)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVOKED"));

		Map<String, Object> row = affiliationRow(affiliation);
		assertThat(row.get("revoked_by")).isEqualTo(admin.getId());
		assertThat(row.get("revocation_reason")).isEqualTo("Estudante desligado do curso");
		Map<String, Object> log = jdbcTemplate.queryForMap(
				"SELECT * FROM admin_audit_logs WHERE target_id = ?", affiliation);
		assertThat(log.get("admin_id")).isEqualTo(admin.getId());
		assertThat(log.get("action")).isEqualTo("AFFILIATION_REVOKED");
		assertThat(log.get("target_type")).isEqualTo("AFFILIATION");
		assertThat(log.get("reason")).isEqualTo("Estudante desligado do curso");
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private UUID createUniversityFor(User account, String name) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, ?, 'APPROVED') RETURNING id",
				UUID.class, account.getId(), name);
	}

	private User createStudentWithProfile(String fullName) throws Exception {
		User newStudent = createUser(UserRole.STUDENT);
		as(newStudent, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"%s\"}".formatted(fullName)))
				.andExpect(status().isOk());
		return newStudent;
	}

	private UUID requestAffiliation(User requester, UUID targetUniversityId) throws Exception {
		String body = as(requester, post(AFFILIATIONS).contentType(MediaType.APPLICATION_JSON)
				.content("{\"universityId\": \"%s\"}".formatted(targetUniversityId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	private UUID approvedAffiliation(User requester) throws Exception {
		UUID affiliation = requestAffiliation(requester, universityId);
		decide(affiliation, "approve", "{}");
		return affiliation;
	}

	private void decide(UUID affiliation, String decision, String body) throws Exception {
		as(university, post(AFFILIATIONS + "/" + affiliation + "/" + decision)
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());
	}

	private ResultActions revoke(User user, String basePath, UUID affiliation, String body) throws Exception {
		return as(user, post(basePath + "/" + affiliation + "/revoke")
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}

	/** Teste e requisição compartilham a transação: sincroniza antes de ler via JDBC. */
	private Map<String, Object> affiliationRow(UUID affiliationId) {
		entityManager.flush();
		return jdbcTemplate.queryForMap("SELECT * FROM university_affiliations WHERE id = ?", affiliationId);
	}

	private int affiliationCount() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM university_affiliations", Integer.class);
	}
}
