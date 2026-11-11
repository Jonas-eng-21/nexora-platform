package br.com.nexora.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class SkillCatalogTests {

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
	void catalogIsSeededAndListedWithPagination() throws Exception {
		as(createUser(UserRole.STUDENT), get("/api/v1/skills").param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(5))
				.andExpect(jsonPath("$.content[0].id").isNotEmpty())
				.andExpect(jsonPath("$.content[0].name").isNotEmpty())
				.andExpect(jsonPath("$.totalElements", greaterThan(30)));
	}

	@Test
	void searchIgnoresCaseAndAccents() throws Exception {
		User company = createUser(UserRole.COMPANY);

		as(company, get("/api/v1/skills").param("search", "SPRING"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].name", hasItem("Spring Boot")));
		as(company, get("/api/v1/skills").param("search", "analise"))
				.andExpect(jsonPath("$.content[*].name", hasItem("Análise de Dados")));
		as(company, get("/api/v1/skills").param("search", "nao-existe-" + UUID.randomUUID()))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void catalogRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/skills")).andExpect(status().isUnauthorized());
	}

	@Test
	void studentAddsNewSkillToCatalog() throws Exception {
		String name = uniqueName();

		createSkill(createUser(UserRole.STUDENT), name)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value(name));

		as(createUser(UserRole.COMPANY), get("/api/v1/skills").param("search", name))
				.andExpect(jsonPath("$.content[*].name", hasItem(name)));
	}

	@Test
	void logicalDuplicatesReturnTheExistingSkill() throws Exception {
		User company = createUser(UserRole.COMPANY);
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String body = createSkill(company, "Programação Ágil " + suffix)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		int before = skillCount();

		// Mesma competência com outra caixa, sem acentos e com espaços extras.
		createSkill(company, "  programacao   AGIL " + suffix + " ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.name").value("Programação Ágil " + suffix));
		createSkill(createUser(UserRole.STUDENT), "java")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Java"));

		assertThat(skillCount()).isEqualTo(before);
	}

	@Test
	void universityCannotAddSkills() throws Exception {
		int before = skillCount();

		createSkill(createUser(UserRole.UNIVERSITY), uniqueName()).andExpect(status().isForbidden());

		assertThat(skillCount()).isEqualTo(before);
	}

	@Test
	void invalidSkillNameIsRejected() throws Exception {
		User student = createUser(UserRole.STUDENT);

		createSkill(student, "   ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		createSkill(student, "x".repeat(81)).andExpect(status().isBadRequest());
	}

	private static String uniqueName() {
		return "Skill " + UUID.randomUUID().toString().substring(0, 8);
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}

	private ResultActions createSkill(User user, String name) throws Exception {
		return as(user, post("/api/v1/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"%s\"}".formatted(name)));
	}

	private int skillCount() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM skills", Integer.class);
	}
}
