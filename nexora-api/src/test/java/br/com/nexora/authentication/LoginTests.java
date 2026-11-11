package br.com.nexora.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;

@SpringBootTest
@Transactional
class LoginTests {

	private static final String PASSWORD = "S3nha-forte!";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtDecoder jwtDecoder;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void activeUserLogsInAndTokenGrantsAccessToProtectedRoutes() throws Exception {
		User student = createUser("student@nexora.test", UserRole.STUDENT, AccountStatus.ACTIVE);

		String body = login("student@nexora.test", PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andExpect(jsonPath("$.role").value("STUDENT"))
				.andExpect(content().string(not(containsString(PASSWORD))))
				.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(body, "$.accessToken");

		mockMvc.perform(get("/test-security/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(student.getId().toString()));
	}

	@Test
	void issuedTokenExpires() throws Exception {
		createUser("expiry@nexora.test", UserRole.COMPANY, AccountStatus.ACTIVE);

		String body = login("expiry@nexora.test", PASSWORD).andReturn().getResponse().getContentAsString();
		Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));

		assertThat(jwt.getExpiresAt()).isNotNull();
		assertThat(Duration.between(Instant.now(), jwt.getExpiresAt()))
				.isBetween(Duration.ofMinutes(59), Duration.ofMinutes(60));
	}

	@Test
	void emailIsCaseInsensitiveOnLogin() throws Exception {
		createUser("case@nexora.test", UserRole.STUDENT, AccountStatus.ACTIVE);

		login("  Case@Nexora.TEST ", PASSWORD).andExpect(status().isOk());
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		createUser("wrong@nexora.test", UserRole.STUDENT, AccountStatus.ACTIVE);

		expectInvalidCredentials(login("wrong@nexora.test", "outra-senha-qualquer"));
	}

	@Test
	void unknownEmailIsRejectedWithSameResponseAsWrongPassword() throws Exception {
		expectInvalidCredentials(login("nobody@nexora.test", PASSWORD));
	}

	@Test
	void universityPendingApprovalCannotLogIn() throws Exception {
		createUser("pending@nexora.test", UserRole.UNIVERSITY, AccountStatus.PENDING_APPROVAL);

		login("pending@nexora.test", PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_APPROVAL"))
				.andExpect(jsonPath("$.accessToken").doesNotExist());
	}

	@Test
	void suspendedUserCannotLogIn() throws Exception {
		createUser("suspended@nexora.test", UserRole.COMPANY, AccountStatus.SUSPENDED);

		login("suspended@nexora.test", PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
				.andExpect(jsonPath("$.accessToken").doesNotExist());
	}

	@Test
	void accountStatusIsNotRevealedWithoutTheCorrectPassword() throws Exception {
		createUser("hidden@nexora.test", UserRole.COMPANY, AccountStatus.SUSPENDED);

		expectInvalidCredentials(login("hidden@nexora.test", "outra-senha-qualquer"));
	}

	@Test
	void blankCredentialsAreRejectedWithFieldErrors() throws Exception {
		login("", "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private User createUser(String email, UserRole role, AccountStatus status) {
		return userRepository.saveAndFlush(new User(email, passwordEncoder.encode(PASSWORD), role, status));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private static void expectInvalidCredentials(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."))
				.andExpect(jsonPath("$.accessToken").doesNotExist());
	}
}
