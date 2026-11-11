package br.com.nexora.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
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
class AdminUserManagementTests {

	private static final String REASON = "{\"reason\": \"Violação dos termos de uso\"}";

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
		admin = createUser(UserRole.ADMIN, AccountStatus.ACTIVE);
	}

	@Test
	void adminRoutesAreRestrictedToAdmins() throws Exception {
		User student = createUser(UserRole.STUDENT, AccountStatus.ACTIVE);
		User target = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);

		mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
		as(student, get("/api/v1/admin/users")).andExpect(status().isForbidden());
		as(student, get("/api/v1/admin/users/" + target.getId())).andExpect(status().isForbidden());
		as(student, get("/api/v1/admin/audit-logs")).andExpect(status().isForbidden());
		as(student, post("/api/v1/admin/users/" + target.getId() + "/suspend")
				.contentType(MediaType.APPLICATION_JSON).content(REASON))
				.andExpect(status().isForbidden());

		assertThat(statusOf(target)).isEqualTo("ACTIVE");
		assertThat(auditCount()).isZero();
	}

	@Test
	void adminListsUsersWithPaginationAndFilters() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		createUser(UserRole.STUDENT, AccountStatus.ACTIVE);
		createUser(UserRole.UNIVERSITY, AccountStatus.PENDING_APPROVAL);

		as(admin, get("/api/v1/admin/users").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(4))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());

		as(admin, get("/api/v1/admin/users").param("role", "COMPANY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].role", everyItem(is("COMPANY"))))
				.andExpect(jsonPath("$.content[*].id", hasItem(company.getId().toString())));

		as(admin, get("/api/v1/admin/users").param("status", "PENDING_APPROVAL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].role").value("UNIVERSITY"));
	}

	@Test
	void adminReadsSingleUserAndUnknownUserIs404() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);

		as(admin, get("/api/v1/admin/users/" + company.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(company.getEmail()));
		as(admin, get("/api/v1/admin/users/" + UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void suspensionBlocksTheUserAndIsAudited() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		String companyToken = tokenService.issue(company);
		withToken(companyToken, get("/api/v1/users/me")).andExpect(status().isOk());

		suspend(company)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(company.getId().toString()))
				.andExpect(jsonPath("$.status").value("SUSPENDED"));

		assertThat(statusOf(company)).isEqualTo("SUSPENDED");
		withToken(companyToken, get("/api/v1/users/me"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));

		Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_audit_logs");
		assertThat(log.get("admin_id")).isEqualTo(admin.getId());
		assertThat(log.get("action")).isEqualTo("USER_SUSPENDED");
		assertThat(log.get("target_type")).isEqualTo("USER");
		assertThat(log.get("target_id")).isEqualTo(company.getId());
		assertThat(log.get("reason")).isEqualTo("Violação dos termos de uso");
		assertThat(log.get("created_at")).isNotNull();
	}

	@Test
	void reactivationRestoresAccessAndIsAudited() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.SUSPENDED);
		String companyToken = tokenService.issue(company);

		reactivate(company)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		withToken(companyToken, get("/api/v1/users/me")).andExpect(status().isOk());
		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("SELECT action FROM admin_audit_logs", String.class))
				.isEqualTo("USER_REACTIVATED");
	}

	@Test
	void invalidTransitionsAreRejectedWithoutAudit() throws Exception {
		User active = createUser(UserRole.STUDENT, AccountStatus.ACTIVE);
		User suspended = createUser(UserRole.STUDENT, AccountStatus.SUSPENDED);
		User pendingUniversity = createUser(UserRole.UNIVERSITY, AccountStatus.PENDING_APPROVAL);

		reactivate(active).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
		suspend(suspended).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
		// A aprovação de universidade tem fluxo próprio: não pode ser contornada por suspender/reativar.
		reactivate(pendingUniversity).andExpect(status().isConflict());
		suspend(pendingUniversity).andExpect(status().isConflict());

		assertThat(statusOf(pendingUniversity)).isEqualTo("PENDING_APPROVAL");
		assertThat(auditCount()).isZero();
	}

	@Test
	void adminCannotSuspendOwnAccount() throws Exception {
		suspend(admin).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CANNOT_SUSPEND_SELF"));

		assertThat(statusOf(admin)).isEqualTo("ACTIVE");
		assertThat(auditCount()).isZero();
	}

	@Test
	void reasonIsRequired() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);

		as(admin, post("/api/v1/admin/users/" + company.getId() + "/suspend")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		assertThat(statusOf(company)).isEqualTo("ACTIVE");
		assertThat(auditCount()).isZero();
	}

	@Test
	void unknownTargetIs404() throws Exception {
		as(admin, post("/api/v1/admin/users/" + UUID.randomUUID() + "/suspend")
				.contentType(MediaType.APPLICATION_JSON).content(REASON))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void adminReadsAuditLogsNewestFirst() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		suspend(company).andExpect(status().isOk());
		reactivate(company).andExpect(status().isOk());

		as(admin, get("/api/v1/admin/audit-logs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].action").value("USER_REACTIVATED"))
				.andExpect(jsonPath("$.content[1].action").value("USER_SUSPENDED"))
				.andExpect(jsonPath("$.content[1].adminId").value(admin.getId().toString()))
				.andExpect(jsonPath("$.content[1].targetId").value(company.getId().toString()))
				.andExpect(jsonPath("$.content[1].reason").value("Violação dos termos de uso"))
				.andExpect(jsonPath("$.content[1].createdAt").isNotEmpty())
				.andExpect(jsonPath("$.content[*].action", not(hasItem("UNKNOWN"))));
	}

	private User createUser(UserRole role, AccountStatus status) {
		return userRepository.saveAndFlush(new User(UUID.randomUUID() + "@nexora.test", "hash", role, status));
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return withToken(tokenService.issue(user), request);
	}

	private ResultActions withToken(String token, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions suspend(User target) throws Exception {
		return as(admin, post("/api/v1/admin/users/" + target.getId() + "/suspend")
				.contentType(MediaType.APPLICATION_JSON).content(REASON));
	}

	private ResultActions reactivate(User target) throws Exception {
		return as(admin, post("/api/v1/admin/users/" + target.getId() + "/reactivate")
				.contentType(MediaType.APPLICATION_JSON).content(REASON));
	}

	/** Teste e requisição compartilham a transação: sincroniza antes de ler via JDBC. */
	private String statusOf(User user) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class, user.getId());
	}

	private Integer auditCount() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_logs", Integer.class);
	}
}
