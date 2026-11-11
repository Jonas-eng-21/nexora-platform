package br.com.nexora.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
class UserAccountTests {

	private static final String PASSWORD = "S3nha-forte!";
	private static final String NEW_PASSWORD = "Nova-s3nha-forte!";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private EntityManager entityManager;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void authenticatedUserReadsOwnAccountWithoutSecrets() throws Exception {
		User student = createUser("me@nexora.test", UserRole.STUDENT);

		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(student)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(student.getId().toString()))
				.andExpect(jsonPath("$.email").value("me@nexora.test"))
				.andExpect(jsonPath("$.role").value("STUDENT"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(content().string(not(containsString(student.getPasswordHash()))));
	}

	@Test
	void accountRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void userChangesOwnEmail() throws Exception {
		User company = createUser("old@nexora.test", UserRole.COMPANY);

		update(company, """
				{"currentPassword": "%s", "email": "New@Nexora.test"}
				""".formatted(PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("new@nexora.test"));

		assertThat(reload(company).getEmail()).isEqualTo("new@nexora.test");
	}

	@Test
	void userChangesOwnPassword() throws Exception {
		User student = createUser("pwd@nexora.test", UserRole.STUDENT);

		update(student, """
				{"currentPassword": "%s", "newPassword": "%s"}
				""".formatted(PASSWORD, NEW_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString(NEW_PASSWORD))));

		String storedHash = reload(student).getPasswordHash();
		assertThat(passwordEncoder.matches(NEW_PASSWORD, storedHash)).isTrue();
		assertThat(passwordEncoder.matches(PASSWORD, storedHash)).isFalse();
	}

	@Test
	void wrongCurrentPasswordBlocksAnyChange() throws Exception {
		User student = createUser("guard@nexora.test", UserRole.STUDENT);

		update(student, """
				{"currentPassword": "senha-errada", "email": "hijack@nexora.test", "newPassword": "%s"}
				""".formatted(NEW_PASSWORD))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

		User reloaded = reload(student);
		assertThat(reloaded.getEmail()).isEqualTo("guard@nexora.test");
		assertThat(passwordEncoder.matches(PASSWORD, reloaded.getPasswordHash())).isTrue();
	}

	@Test
	void roleAndStatusCannotBeChangedThroughPayload() throws Exception {
		User student = createUser("escalate@nexora.test", UserRole.STUDENT);

		update(student, """
				{"currentPassword": "%s", "role": "ADMIN", "status": "SUSPENDED", "id": "00000000-0000-0000-0000-000000000000"}
				""".formatted(PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("STUDENT"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.id").value(student.getId().toString()));

		User reloaded = reload(student);
		assertThat(reloaded.getRole()).isEqualTo(UserRole.STUDENT);
		assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void updateNeverTouchesAnotherUsersAccount() throws Exception {
		User owner = createUser("owner@nexora.test", UserRole.STUDENT);
		User other = createUser("other@nexora.test", UserRole.STUDENT);

		update(owner, """
				{"currentPassword": "%s", "email": "owner-new@nexora.test", "newPassword": "%s"}
				""".formatted(PASSWORD, NEW_PASSWORD))
				.andExpect(status().isOk());

		User reloadedOther = reload(other);
		assertThat(reloadedOther.getEmail()).isEqualTo("other@nexora.test");
		assertThat(passwordEncoder.matches(PASSWORD, reloadedOther.getPasswordHash())).isTrue();
	}

	@Test
	void emailAlreadyUsedByAnotherAccountIsRejected() throws Exception {
		User owner = createUser("first@nexora.test", UserRole.STUDENT);
		createUser("taken@nexora.test", UserRole.COMPANY);

		update(owner, """
				{"currentPassword": "%s", "email": "TAKEN@nexora.test"}
				""".formatted(PASSWORD))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

		assertThat(reload(owner).getEmail()).isEqualTo("first@nexora.test");
	}

	@Test
	void keepingTheSameEmailIsNotAConflict() throws Exception {
		User owner = createUser("same@nexora.test", UserRole.STUDENT);

		update(owner, """
				{"currentPassword": "%s", "email": "same@nexora.test"}
				""".formatted(PASSWORD))
				.andExpect(status().isOk());
	}

	@Test
	void invalidPayloadIsRejected() throws Exception {
		User student = createUser("invalid@nexora.test", UserRole.STUDENT);

		update(student, """
				{"email": "not-an-email", "newPassword": "short"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		update(student, """
				{"currentPassword": "%s", "newPassword": "%s"}
				""".formatted(PASSWORD, "ç".repeat(40)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
	}

	private User createUser(String email, UserRole role) {
		return userRepository.saveAndFlush(
				new User(email, passwordEncoder.encode(PASSWORD), role, AccountStatus.ACTIVE));
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.issue(user);
	}

	private ResultActions update(User user, String body) throws Exception {
		return mockMvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** Teste e requisição compartilham a transação: sincroniza e relê do banco. */
	private User reload(User user) {
		entityManager.flush();
		entityManager.clear();
		return userRepository.findById(user.getId()).orElseThrow();
	}
}
