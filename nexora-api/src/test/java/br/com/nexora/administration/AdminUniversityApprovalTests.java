package br.com.nexora.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class AdminUniversityApprovalTests {

	private static final String BASE = "/api/v1/admin/universities";
	private static final String PASSWORD = "S3nha-forte!";

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

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		admin = createUser(UserRole.ADMIN);
	}

	@Test
	void onlyAdminsCanSeeAndDecideUniversities() throws Exception {
		UUID universityId = registerUniversity("restricted@nexora.test", "Universidade Restrita");

		mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.COMPANY, UserRole.UNIVERSITY }) {
			User user = createUser(role);

			as(user, get(BASE)).andExpect(status().isForbidden());
			as(user, get(BASE + "/" + universityId)).andExpect(status().isForbidden());
			as(user, post(BASE + "/" + universityId + "/approve").contentType(MediaType.APPLICATION_JSON)
					.content("{}")).andExpect(status().isForbidden());
			as(user, post(BASE + "/" + universityId + "/reject").contentType(MediaType.APPLICATION_JSON)
					.content("{\"reason\": \"x\"}")).andExpect(status().isForbidden());
		}

		assertThat(universityRow(universityId).get("approval_status")).isEqualTo("PENDING");
		assertThat(accountStatus("restricted@nexora.test")).isEqualTo("PENDING_APPROVAL");
	}

	@Test
	void adminListsPendingUniversitiesWithInstitutionalData() throws Exception {
		UUID pendingId = registerUniversity("pending@nexora.test", "Universidade Pendente");
		UUID approvedId = registerUniversity("approved@nexora.test", "Universidade Aprovada");
		approve(approvedId).andExpect(status().isOk());

		as(admin, get(BASE).param("approvalStatus", "PENDING"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(pendingId.toString()))
				.andExpect(jsonPath("$.content[0].name").value("Universidade Pendente"))
				.andExpect(jsonPath("$.content[0].acronym").value("UT"))
				.andExpect(jsonPath("$.content[0].email").value("pending@nexora.test"))
				.andExpect(jsonPath("$.content[0].accountStatus").value("PENDING_APPROVAL"))
				.andExpect(jsonPath("$.content[0].approvalStatus").value("PENDING"));
		as(admin, get(BASE))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[*].id", hasItem(approvedId.toString())));
		as(admin, get(BASE + "/" + pendingId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("pending@nexora.test"));
	}

	@Test
	void approvalActivatesTheUniversityAndRecordsTheDecision() throws Exception {
		UUID universityId = registerUniversity("ok@nexora.test", "Universidade Ok");
		login("ok@nexora.test").andExpect(status().isForbidden());

		approve(universityId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
				.andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.approvalDecidedBy").value(admin.getId().toString()))
				.andExpect(jsonPath("$.approvalDecidedAt").isNotEmpty());

		Map<String, Object> row = universityRow(universityId);
		assertThat(row.get("approval_status")).isEqualTo("APPROVED");
		assertThat(row.get("approval_decided_by")).isEqualTo(admin.getId());
		assertThat(row.get("approval_decided_at")).isNotNull();
		assertThat(row.get("approval_reason")).isEqualTo("Documentação conferida");
		assertThat(accountStatus("ok@nexora.test")).isEqualTo("ACTIVE");
		assertAudit("UNIVERSITY_APPROVED", universityId, "Documentação conferida");

		login("ok@nexora.test").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("UNIVERSITY"));
		as(userRepository.findByEmail("ok@nexora.test").orElseThrow(), get("/api/v1/universities/me"))
				.andExpect(status().isOk());
	}

	@Test
	void rejectionRequiresReasonAndGivesNoPowerToTheUniversity() throws Exception {
		UUID universityId = registerUniversity("no@nexora.test", "Universidade Recusada");

		as(admin, post(BASE + "/" + universityId + "/reject").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		assertThat(universityRow(universityId).get("approval_status")).isEqualTo("PENDING");

		reject(universityId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.approvalStatus").value("REJECTED"))
				.andExpect(jsonPath("$.accountStatus").value("REJECTED"))
				.andExpect(jsonPath("$.approvalReason").value("Instituição não reconhecida"));

		Map<String, Object> row = universityRow(universityId);
		assertThat(row.get("approval_decided_by")).isEqualTo(admin.getId());
		assertThat(row.get("approval_decided_at")).isNotNull();
		assertAudit("UNIVERSITY_REJECTED", universityId, "Instituição não reconhecida");

		login("no@nexora.test")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_REJECTED"));
		User rejected = userRepository.findByEmail("no@nexora.test").orElseThrow();
		as(rejected, get("/api/v1/universities/me"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_REJECTED"));
		// A rejeição não pode ser contornada pela reativação de contas.
		as(admin, post("/api/v1/admin/users/" + rejected.getId() + "/reactivate")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"tentativa\"}"))
				.andExpect(status().isConflict());
		assertThat(accountStatus("no@nexora.test")).isEqualTo("REJECTED");
	}

	@Test
	void decisionCannotBeTakenTwice() throws Exception {
		UUID approvedId = registerUniversity("twice-a@nexora.test", "Universidade A");
		UUID rejectedId = registerUniversity("twice-b@nexora.test", "Universidade B");
		approve(approvedId).andExpect(status().isOk());
		reject(rejectedId).andExpect(status().isOk());

		reject(approvedId).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("UNIVERSITY_ALREADY_DECIDED"));
		approve(approvedId).andExpect(status().isConflict());
		approve(rejectedId).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("UNIVERSITY_ALREADY_DECIDED"));

		assertThat(accountStatus("twice-a@nexora.test")).isEqualTo("ACTIVE");
		assertThat(accountStatus("twice-b@nexora.test")).isEqualTo("REJECTED");
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_logs", Integer.class)).isEqualTo(2);
	}

	@Test
	void unknownUniversityIs404() throws Exception {
		approve(UUID.randomUUID()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("UNIVERSITY_NOT_FOUND"));
		reject(UUID.randomUUID()).andExpect(status().isNotFound());
		as(admin, get(BASE + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
		as(admin, get(BASE)).andExpect(jsonPath("$.content[*].name", not(hasItem("inexistente"))));
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private UUID registerUniversity(String email, String name) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s", "role": "UNIVERSITY", "university": {"name": "%s", "acronym": "UT"}}
				""".formatted(email, PASSWORD, name)))
				.andExpect(status().isCreated());
		entityManager.flush();
		return jdbcTemplate.queryForObject("""
				SELECT un.id FROM universities un JOIN users u ON u.id = un.user_id WHERE u.email = ?
				""", UUID.class, email);
	}

	private ResultActions approve(UUID universityId) throws Exception {
		return as(admin, post(BASE + "/" + universityId + "/approve").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Documentação conferida\"}"));
	}

	private ResultActions reject(UUID universityId) throws Exception {
		return as(admin, post(BASE + "/" + universityId + "/reject").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Instituição não reconhecida\"}"));
	}

	private ResultActions login(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}

	private Map<String, Object> universityRow(UUID universityId) {
		entityManager.flush();
		return jdbcTemplate.queryForMap("SELECT * FROM universities WHERE id = ?", universityId);
	}

	private String accountStatus(String email) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT status FROM users WHERE email = ?", String.class, email);
	}

	private void assertAudit(String action, UUID universityId, String reason) {
		Map<String, Object> log = jdbcTemplate.queryForMap(
				"SELECT * FROM admin_audit_logs WHERE target_id = ?", universityId);
		assertThat(log.get("admin_id")).isEqualTo(admin.getId());
		assertThat(log.get("action")).isEqualTo(action);
		assertThat(log.get("target_type")).isEqualTo("UNIVERSITY");
		assertThat(log.get("reason")).isEqualTo(reason);
	}
}
