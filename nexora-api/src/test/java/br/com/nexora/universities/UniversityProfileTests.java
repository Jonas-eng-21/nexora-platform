package br.com.nexora.universities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
class UniversityProfileTests {

	private static final String ME = "/api/v1/universities/me";
	private static final String PASSWORD = "S3nha-forte!";
	private static final String UNIVERSITY_DATA = """
			{"name": "Universidade Federal do Agreste de Pernambuco", "acronym": "UFAPE",
			 "location": "Garanhuns, PE", "website": "https://ufape.edu.br", "description": "Universidade pública."}
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
	void registrationPersistsInstitutionalDataForPendingUniversity() throws Exception {
		String body = registerUniversity("ufape@nexora.test", UNIVERSITY_DATA)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
				.andReturn().getResponse().getContentAsString();
		entityManager.flush();

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM universities");
		assertThat(row.get("user_id")).isEqualTo(UUID.fromString(JsonPath.read(body, "$.id")));
		assertThat(row.get("name")).isEqualTo("Universidade Federal do Agreste de Pernambuco");
		assertThat(row.get("acronym")).isEqualTo("UFAPE");
		assertThat(row.get("location")).isEqualTo("Garanhuns, PE");
		assertThat(row.get("website")).isEqualTo("https://ufape.edu.br");
		assertThat(row.get("description")).isEqualTo("Universidade pública.");
	}

	@Test
	void universityRegistrationRequiresInstitutionalData() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "nodata@nexora.test", "password": "%s", "role": "UNIVERSITY"}
				""".formatted(PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNIVERSITY_DATA_REQUIRED"));
		registerUniversity("invalid@nexora.test", "{\"name\": \" \", \"website\": \"not-a-url\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("university.name")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("university.website")));

		assertThat(userCount("nodata@nexora.test", "invalid@nexora.test")).isZero();
		assertThat(universityRows()).isZero();
	}

	@Test
	void otherRolesCannotSendInstitutionalData() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "student@nexora.test", "password": "%s", "role": "STUDENT", "university": %s}
				""".formatted(PASSWORD, UNIVERSITY_DATA)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNIVERSITY_DATA_NOT_ALLOWED"));

		assertThat(userCount("student@nexora.test")).isZero();
		assertThat(universityRows()).isZero();
	}

	@Test
	void pendingUniversityCannotOperate() throws Exception {
		User pending = registerAndLoad("pending@nexora.test");

		as(pending, get(ME))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_APPROVAL"));
		as(pending, put(ME).contentType(MediaType.APPLICATION_JSON).content(UNIVERSITY_DATA))
				.andExpect(status().isForbidden());
	}

	@Test
	void approvedUniversityReadsAndUpdatesOwnProfile() throws Exception {
		User university = registerActiveUniversity("active@nexora.test");

		as(university, get(ME))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Universidade Federal do Agreste de Pernambuco"))
				.andExpect(jsonPath("$.acronym").value("UFAPE"));

		as(university, put(ME).contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "  UFAPE - Campus Garanhuns ", "acronym": " "}
				"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("UFAPE - Campus Garanhuns"))
				.andExpect(jsonPath("$.acronym").doesNotExist())
				.andExpect(jsonPath("$.website").doesNotExist());

		assertThat(universityRows()).isEqualTo(1);
	}

	@Test
	void universityOnlyAffectsOwnProfile() throws Exception {
		User first = registerActiveUniversity("first@nexora.test");
		User second = registerActiveUniversity("second@nexora.test");

		as(first, put(ME).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Primeira\", \"userId\": \"%s\"}".formatted(second.getId())))
				.andExpect(status().isOk());

		as(second, get(ME)).andExpect(jsonPath("$.name").value("Universidade Federal do Agreste de Pernambuco"));
		as(first, get(ME)).andExpect(jsonPath("$.name").value("Primeira"));
	}

	@Test
	void otherRolesCannotUseUniversityProfileRoutes() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.COMPANY, UserRole.ADMIN }) {
			User user = userRepository.saveAndFlush(
					new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));

			as(user, get(ME)).andExpect(status().isForbidden());
			as(user, put(ME).contentType(MediaType.APPLICATION_JSON).content(UNIVERSITY_DATA))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());
	}

	@Test
	void invalidUpdateIsRejected() throws Exception {
		User university = registerActiveUniversity("invalid-update@nexora.test");

		as(university, put(ME).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \" \", \"website\": \"javascript:alert(1)\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		as(university, get(ME)).andExpect(jsonPath("$.acronym").value("UFAPE"));
	}

	private ResultActions registerUniversity(String email, String universityJson) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s", "role": "UNIVERSITY", "university": %s}
				""".formatted(email, PASSWORD, universityJson)));
	}

	private User registerAndLoad(String email) throws Exception {
		registerUniversity(email, UNIVERSITY_DATA).andExpect(status().isCreated());
		entityManager.flush();
		return userRepository.findByEmail(email).orElseThrow();
	}

	/** Simula a aprovação do administrador (US-15), que ainda não tem endpoint. */
	private User registerActiveUniversity(String email) throws Exception {
		User university = registerAndLoad(email);
		university.changeStatus(AccountStatus.ACTIVE);
		return userRepository.saveAndFlush(university);
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}

	private int universityRows() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM universities", Integer.class);
	}

	private int userCount(String... emails) {
		entityManager.flush();
		int total = 0;
		for (String email : emails) {
			total += jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
		}
		return total;
	}
}
