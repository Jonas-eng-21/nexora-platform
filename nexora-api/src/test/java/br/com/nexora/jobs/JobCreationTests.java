package br.com.nexora.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
class JobCreationTests {

	private static final String JOBS = "/api/v1/jobs";

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
	private UUID java;
	private UUID spring;
	private UUID docker;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		java = skillId("java");
		spring = skillId("spring boot");
		docker = skillId("docker");
	}

	@Test
	void companyPublishesJobLinkedToOwnProfile() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		String companyId = createCompanyProfile(company, "Tech Garanhuns");

		createJob(company, validJob())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.companyId").value(companyId))
				.andExpect(jsonPath("$.companyName").value("Tech Garanhuns"))
				.andExpect(jsonPath("$.title").value("Estágio em Backend Java"))
				.andExpect(jsonPath("$.description").value("Desenvolver APIs REST."))
				.andExpect(jsonPath("$.area").value("BACKEND"))
				.andExpect(jsonPath("$.location").value("Garanhuns, PE"))
				.andExpect(jsonPath("$.workModel").value("HYBRID"))
				.andExpect(jsonPath("$.level").value("INTERNSHIP"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.requiredSkills.length()").value(2))
				.andExpect(jsonPath("$.requiredSkills[0].name").value("Java"))
				.andExpect(jsonPath("$.requiredSkills[1].name").value("Spring Boot"))
				.andExpect(jsonPath("$.desiredSkills.length()").value(1))
				.andExpect(jsonPath("$.desiredSkills[0].name").value("Docker"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());

		entityManager.flush();
		Map<String, Object> job = jdbcTemplate.queryForMap("SELECT * FROM jobs");
		assertThat(job.get("company_id")).isEqualTo(UUID.fromString(companyId));
		assertThat(job.get("status")).isEqualTo("OPEN");
		List<Map<String, Object>> skills = jdbcTemplate.queryForList(
				"SELECT skill_id, requirement FROM job_skills ORDER BY requirement, skill_id");
		assertThat(skills).hasSize(3);
		assertThat(skills).extracting(row -> row.get("requirement"))
				.containsExactly("DESIRED", "REQUIRED", "REQUIRED");
	}

	@Test
	void jobMayBeCreatedClosedAndWithoutOptionalFields() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		createCompanyProfile(company, "Tech Garanhuns");

		createJob(company, """
				{"title": "Desenvolvedor Júnior", "description": "Atuar no time de produto.",
				 "area": "BACKEND", "workModel": "REMOTE", "level": "JUNIOR", "status": "CLOSED",
				 "requiredSkillIds": ["%s"]}
				""".formatted(java))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("CLOSED"))
				.andExpect(jsonPath("$.location").doesNotExist())
				.andExpect(jsonPath("$.desiredSkills.length()").value(0));
	}

	@Test
	void ownerAlwaysComesFromTokenNotFromPayload() throws Exception {
		User alpha = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		User beta = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		String alphaCompanyId = createCompanyProfile(alpha, "Alpha");
		String betaCompanyId = createCompanyProfile(beta, "Beta");

		String body = validJob().replaceFirst("\\{", "{\"companyId\": \"%s\", ".formatted(betaCompanyId));
		createJob(alpha, body)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.companyId").value(alphaCompanyId))
				.andExpect(jsonPath("$.companyName").value("Alpha"));

		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM jobs WHERE company_id = ?", Integer.class,
				UUID.fromString(betaCompanyId))).isZero();
	}

	@Test
	void companyWithoutProfileCannotPublish() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);

		createJob(company, validJob())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMPANY_PROFILE_NOT_FOUND"));

		assertThat(jobRows()).isZero();
	}

	@Test
	void requiredFieldsAreValidated() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		createCompanyProfile(company, "Tech Garanhuns");

		createJob(company, validJob().replace("\"Estágio em Backend Java\"", "\" \""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("title"));
		createJob(company, validJob().replace("\"workModel\": \"HYBRID\",", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("workModel"));
		createJob(company, validJob().replace("\"area\": \"BACKEND\",", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("area"));
		createJob(company, """
				{"title": "Vaga", "description": "Descrição.", "area": "BACKEND", "workModel": "REMOTE",
				 "level": "JUNIOR", "requiredSkillIds": []}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("requiredSkillIds"));
		createJob(company, validJob().replace("\"HYBRID\"", "\"PRESENCIAL\""))
				.andExpect(status().isBadRequest());
		// A área vem do catálogo fixo: texto livre não é aceito.
		createJob(company, validJob().replace("\"area\": \"BACKEND\"", "\"area\": \"Desenvolvimento Backend\""))
				.andExpect(status().isBadRequest());

		assertThat(jobRows()).isZero();
	}

	@Test
	void unknownSkillIsRejected() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		createCompanyProfile(company, "Tech Garanhuns");

		createJob(company, validJob().replace(docker.toString(), UUID.randomUUID().toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SKILL_NOT_FOUND"));

		assertThat(jobRows()).isZero();
	}

	@Test
	void skillCannotBeRequiredAndDesiredAtOnce() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		createCompanyProfile(company, "Tech Garanhuns");

		createJob(company, validJob().replace(docker.toString(), java.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("JOB_SKILL_CONFLICT"));

		assertThat(jobRows()).isZero();
	}

	@Test
	void onlyActiveCompaniesCanPublish() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			createJob(createUser(role, AccountStatus.ACTIVE), validJob()).andExpect(status().isForbidden());
		}
		mockMvc.perform(post(JOBS).contentType(MediaType.APPLICATION_JSON).content(validJob()))
				.andExpect(status().isUnauthorized());

		User suspended = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		createCompanyProfile(suspended, "Suspensa");
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
		entityManager.clear();
		createJob(suspended, validJob())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));

		assertThat(jobRows()).isZero();
	}

	private String validJob() {
		return """
				{"title": "Estágio em Backend Java", "description": "Desenvolver APIs REST.",
				 "area": "BACKEND", "location": "Garanhuns, PE", "workModel": "HYBRID",
				 "level": "INTERNSHIP", "requiredSkillIds": ["%s", "%s"], "desiredSkillIds": ["%s"]}
				""".formatted(java, spring, docker);
	}

	private UUID skillId(String normalizedName) {
		return jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = ?", UUID.class,
				normalizedName);
	}

	private User createUser(UserRole role, AccountStatus status) {
		return userRepository.saveAndFlush(new User(UUID.randomUUID() + "@nexora.test", "hash", role, status));
	}

	private String createCompanyProfile(User company, String name) throws Exception {
		String response = as(company, put("/api/v1/companies/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"%s\"}".formatted(name)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}

	private ResultActions createJob(User user, String body) throws Exception {
		return as(user, post(JOBS).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private int jobRows() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM jobs", Integer.class);
	}
}

