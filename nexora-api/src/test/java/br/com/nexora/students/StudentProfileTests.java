package br.com.nexora.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class StudentProfileTests {

	private static final String FULL_PROFILE = """
			{"fullName": "Ana Souza", "course": "Ciência da Computação", "institution": "UFAPE",
			 "location": "Garanhuns, PE", "summary": "Estudante interessada em backend."}
			""";

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

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void studentCreatesAndReadsOwnProfile() throws Exception {
		User student = createUser(UserRole.STUDENT);

		putProfile(student, FULL_PROFILE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.fullName").value("Ana Souza"))
				.andExpect(jsonPath("$.course").value("Ciência da Computação"))
				.andExpect(jsonPath("$.institution").value("UFAPE"))
				.andExpect(jsonPath("$.location").value("Garanhuns, PE"))
				.andExpect(jsonPath("$.summary").value("Estudante interessada em backend."));

		as(student, get("/api/v1/students/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Ana Souza"));
		assertThat(profileOwnerIds()).containsExactly(student.getId());
	}

	@Test
	void profileNotCreatedYetIs404() throws Exception {
		as(createUser(UserRole.STUDENT), get("/api/v1/students/me"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_FOUND"));
	}

	@Test
	void updateReplacesProfileWithoutDuplicating() throws Exception {
		User student = createUser(UserRole.STUDENT);
		putProfile(student, FULL_PROFILE).andExpect(status().isOk());

		putProfile(student, """
				{"fullName": "  Ana S. Souza  ", "course": "Engenharia de Software", "summary": "   "}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Ana S. Souza"))
				.andExpect(jsonPath("$.course").value("Engenharia de Software"))
				.andExpect(jsonPath("$.institution").doesNotExist())
				.andExpect(jsonPath("$.location").doesNotExist())
				.andExpect(jsonPath("$.summary").doesNotExist());

		assertThat(profileOwnerIds()).containsExactly(student.getId());
	}

	@Test
	void studentOnlyAffectsOwnProfile() throws Exception {
		User ana = createUser(UserRole.STUDENT);
		User bruno = createUser(UserRole.STUDENT);
		putProfile(bruno, """
				{"fullName": "Bruno Lima"}
				""").andExpect(status().isOk());

		// Identificadores enviados no payload não têm autoridade: o dono vem do token.
		putProfile(ana, """
				{"fullName": "Ana Souza", "userId": "%s", "id": "%s"}
				""".formatted(bruno.getId(), bruno.getId()))
				.andExpect(status().isOk());

		as(bruno, get("/api/v1/students/me")).andExpect(jsonPath("$.fullName").value("Bruno Lima"));
		as(ana, get("/api/v1/students/me")).andExpect(jsonPath("$.fullName").value("Ana Souza"));
		assertThat(profileOwnerIds()).containsExactlyInAnyOrder(ana.getId(), bruno.getId());
	}

	@Test
	void otherRolesCannotUseStudentProfileRoutes() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.COMPANY, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			User user = createUser(role);

			as(user, get("/api/v1/students/me")).andExpect(status().isForbidden());
			putProfile(user, FULL_PROFILE).andExpect(status().isForbidden());
		}
		mockMvc.perform(get("/api/v1/students/me")).andExpect(status().isUnauthorized());

		assertThat(profileOwnerIds()).isEmpty();
	}

	@Test
	void invalidProfileIsRejected() throws Exception {
		User student = createUser(UserRole.STUDENT);

		putProfile(student, """
				{"fullName": " ", "course": "Sistemas de Informação"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("fullName"));
		putProfile(student, """
				{"fullName": "Ana Souza", "summary": "%s"}
				""".formatted("x".repeat(2001)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("summary"));

		assertThat(profileOwnerIds()).isEmpty();
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}

	private ResultActions putProfile(User user, String body) throws Exception {
		return as(user, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** Teste e requisição compartilham a transação: sincroniza antes de ler via JDBC. */
	private java.util.List<UUID> profileOwnerIds() {
		entityManager.flush();
		return jdbcTemplate.queryForList("SELECT user_id FROM students").stream()
				.map(Map::values).map(values -> (UUID) values.iterator().next()).toList();
	}
}
