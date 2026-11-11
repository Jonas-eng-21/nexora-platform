package br.com.nexora.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class RegistrationTests {

	private static final String PASSWORD = "S3nha-forte!";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void studentRegistersAsActiveWithoutAuthentication() throws Exception {
		register("student@nexora.test", PASSWORD, "STUDENT")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.email").value("student@nexora.test"))
				.andExpect(jsonPath("$.role").value("STUDENT"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void companyRegistersAsActive() throws Exception {
		register("company@nexora.test", PASSWORD, "COMPANY")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("COMPANY"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void universityRegistersAsPendingApproval() throws Exception {
		register("university@nexora.test", PASSWORD, "UNIVERSITY")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("UNIVERSITY"))
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
	}

	@Test
	void statusSentByClientIsIgnored() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "sneaky@nexora.test", "password": "%s", "role": "UNIVERSITY", "status": "ACTIVE"}
				""".formatted(PASSWORD)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
	}

	@Test
	void adminCannotBeCreatedThroughPublicRegistration() throws Exception {
		register("admin@nexora.test", PASSWORD, "ADMIN")
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));

		assertThat(countUsers("admin@nexora.test")).isZero();
	}

	@Test
	void passwordIsStoredHashedAndNeverReturned() throws Exception {
		register("hash@nexora.test", PASSWORD, "STUDENT")
				.andExpect(status().isCreated())
				.andExpect(content().string(not(containsString(PASSWORD))))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		entityManager.flush(); // teste e requisição compartilham a transação: envia o INSERT antes do JDBC
		String storedHash = jdbcTemplate.queryForObject(
				"SELECT password_hash FROM users WHERE email = 'hash@nexora.test'", String.class);
		assertThat(storedHash).isNotEqualTo(PASSWORD);
		assertThat(new BCryptPasswordEncoder().matches(PASSWORD, storedHash)).isTrue();
	}

	@Test
	void duplicateEmailIsRejectedIgnoringCase() throws Exception {
		register("dup@nexora.test", PASSWORD, "STUDENT").andExpect(status().isCreated());

		register("DUP@Nexora.test", PASSWORD, "COMPANY")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

		assertThat(countUsers("dup@nexora.test")).isEqualTo(1);
	}

	@Test
	void invalidPayloadIsRejectedWithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "not-an-email", "password": "short"}
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("email")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("password")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("role")));
	}

	@Test
	void passwordLongerThanBcryptLimitIsRejected() throws Exception {
		register("long@nexora.test", "ç".repeat(40), "STUDENT")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
	}

	private ResultActions register(String email, String password, String role) throws Exception {
		String body = toJson(Map.of("email", email, "password", password, "role", role));
		return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private Integer countUsers(String email) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
	}

	private static String toJson(Map<String, String> fields) {
		StringBuilder json = new StringBuilder("{");
		fields.forEach((key, value) -> json.append('"').append(key).append("\":\"").append(value).append("\","));
		json.setCharAt(json.length() - 1, '}');
		return json.toString();
	}
}
