package br.com.nexora.companies;

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

import com.jayway.jsonpath.JsonPath;

import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class CompanyProfileTests {

	private static final String ME = "/api/v1/companies/me";
	private static final String FULL_PROFILE = """
			{"name": "Tech Garanhuns", "sector": "Software", "description": "Fábrica de software.",
			 "location": "Garanhuns, PE"}
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
	void companyCreatesAndReadsOwnProfile() throws Exception {
		User company = createUser(UserRole.COMPANY);
		as(company, get(ME)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMPANY_PROFILE_NOT_FOUND"));

		putProfile(company, FULL_PROFILE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Tech Garanhuns"))
				.andExpect(jsonPath("$.sector").value("Software"))
				.andExpect(jsonPath("$.description").value("Fábrica de software."))
				.andExpect(jsonPath("$.location").value("Garanhuns, PE"));

		as(company, get(ME)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tech Garanhuns"));
		assertThat(companyRows()).isEqualTo(1);
	}

	@Test
	void updateReplacesProfileWithoutDuplicating() throws Exception {
		User company = createUser(UserRole.COMPANY);
		putProfile(company, FULL_PROFILE).andExpect(status().isOk());

		putProfile(company, """
				{"name": "  Tech Garanhuns LTDA ", "sector": " "}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Tech Garanhuns LTDA"))
				.andExpect(jsonPath("$.sector").doesNotExist())
				.andExpect(jsonPath("$.description").doesNotExist())
				.andExpect(jsonPath("$.location").doesNotExist());

		assertThat(companyRows()).isEqualTo(1);
	}

	@Test
	void companyOnlyAffectsOwnProfile() throws Exception {
		User alpha = createUser(UserRole.COMPANY);
		User beta = createUser(UserRole.COMPANY);
		String betaId = JsonPath.read(putProfile(beta, "{\"name\": \"Beta\"}")
				.andReturn().getResponse().getContentAsString(), "$.id");

		// Identificadores enviados no payload não têm autoridade: o dono vem do token.
		putProfile(alpha, """
				{"name": "Alpha", "id": "%s", "userId": "%s"}
				""".formatted(betaId, beta.getId()))
				.andExpect(status().isOk());

		as(beta, get(ME)).andExpect(jsonPath("$.name").value("Beta"));
		as(alpha, get(ME)).andExpect(jsonPath("$.name").value("Alpha"));
		assertThat(companyRows()).isEqualTo(2);
	}

	@Test
	void otherRolesCannotUseCompanyProfileRoutes() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			User user = createUser(role);

			as(user, get(ME)).andExpect(status().isForbidden());
			putProfile(user, FULL_PROFILE).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());

		assertThat(companyRows()).isZero();
	}

	@Test
	void invalidProfileIsRejected() throws Exception {
		User company = createUser(UserRole.COMPANY);

		putProfile(company, "{\"name\": \" \"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("name"));
		putProfile(company, "{\"name\": \"Ok\", \"description\": \"%s\"}".formatted("x".repeat(2001)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("description"));

		assertThat(companyRows()).isZero();
	}

	@Test
	void adminReadsAndIntervenesWithAudit() throws Exception {
		User admin = createUser(UserRole.ADMIN);
		User company = createUser(UserRole.COMPANY);
		String companyId = JsonPath.read(putProfile(company, FULL_PROFILE)
				.andReturn().getResponse().getContentAsString(), "$.id");

		as(admin, get("/api/v1/admin/companies/" + companyId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Tech Garanhuns"));

		as(admin, put("/api/v1/admin/companies/" + companyId).contentType(MediaType.APPLICATION_JSON).content("""
				{"profile": {"name": "Tech Garanhuns", "sector": "Software", "description": "Descrição moderada."},
				 "reason": "Conteúdo impróprio na descrição"}
				"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("Descrição moderada."));

		as(company, get(ME)).andExpect(jsonPath("$.description").value("Descrição moderada."));
		entityManager.flush();
		Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_audit_logs");
		assertThat(log.get("admin_id")).isEqualTo(admin.getId());
		assertThat(log.get("action")).isEqualTo("COMPANY_PROFILE_UPDATED");
		assertThat(log.get("target_type")).isEqualTo("COMPANY");
		assertThat(log.get("target_id")).isEqualTo(UUID.fromString(companyId));
		assertThat(log.get("reason")).isEqualTo("Conteúdo impróprio na descrição");
	}

	@Test
	void adminInterventionRequiresReasonAndExistingCompany() throws Exception {
		User admin = createUser(UserRole.ADMIN);
		User company = createUser(UserRole.COMPANY);
		String companyId = JsonPath.read(putProfile(company, FULL_PROFILE)
				.andReturn().getResponse().getContentAsString(), "$.id");

		as(admin, put("/api/v1/admin/companies/" + companyId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"profile\": {\"name\": \"Outro nome\"}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		as(admin, put("/api/v1/admin/companies/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"profile\": {\"name\": \"Outro nome\"}, \"reason\": \"x\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
		as(admin, get("/api/v1/admin/companies/" + UUID.randomUUID())).andExpect(status().isNotFound());

		as(company, get(ME)).andExpect(jsonPath("$.name").value("Tech Garanhuns"));
		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_logs", Integer.class)).isZero();
	}

	@Test
	void nonAdminsCannotUseAdminCompanyRoutes() throws Exception {
		User company = createUser(UserRole.COMPANY);
		User otherCompany = createUser(UserRole.COMPANY);
		String companyId = JsonPath.read(putProfile(company, FULL_PROFILE)
				.andReturn().getResponse().getContentAsString(), "$.id");

		as(otherCompany, get("/api/v1/admin/companies/" + companyId)).andExpect(status().isForbidden());
		as(otherCompany, put("/api/v1/admin/companies/" + companyId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"profile\": {\"name\": \"Invadida\"}, \"reason\": \"x\"}"))
				.andExpect(status().isForbidden());

		as(company, get(ME)).andExpect(jsonPath("$.name").value("Tech Garanhuns"));
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}

	private ResultActions putProfile(User user, String body) throws Exception {
		return as(user, put(ME).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private int companyRows() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM companies", Integer.class);
	}
}
