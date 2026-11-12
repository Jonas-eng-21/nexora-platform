package br.com.nexora.matching;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

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

@SpringBootTest
@Transactional
class JobMatchEndpointTests {

	private static final String MATCH = "/api/v1/matching/jobs/";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;
	private UUID java;
	private UUID spring;
	private UUID docker;
	private User company;
	private String jobId;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		java = skillId("java");
		spring = skillId("spring boot");
		docker = skillId("docker");
		company = createUser(UserRole.COMPANY);
		as(company, put("/api/v1/companies/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Tech Garanhuns\"}")).andExpect(status().isOk());
		jobId = JsonPath.read(as(company, post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "Estágio em Backend Java", "description": "Desenvolver APIs REST.", "area": "BACKEND",
				 "location": "Garanhuns - PE", "workModel": "HYBRID", "level": "INTERNSHIP",
				 "requiredSkillIds": ["%s", "%s"], "desiredSkillIds": ["%s"]}
				""".formatted(java, spring, docker)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");
	}

	@Test
	void studentSeesExplainableScoreBuiltFromBaseResumeAndCatalogSkills() throws Exception {
		User student = createStudent("Garanhuns, PE", "[\"BACKEND\"]", "[\"HYBRID\"]", java, docker);

		// Obrigatórias 1/2 (22,5) + desejáveis 1/1 (20) + interesse (20) + modelo e cidade (15) = 77,5 -> 78.
		as(student, get(MATCH + jobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobId").value(jobId))
				.andExpect(jsonPath("$.jobTitle").value("Estágio em Backend Java"))
				.andExpect(jsonPath("$.match.score").value(78))
				.andExpect(jsonPath("$.match.algorithmVersion").value("v1"))
				.andExpect(jsonPath("$.match.matchedRequiredSkills[0]").value("Java"))
				.andExpect(jsonPath("$.match.missingRequiredSkills[0]").value("Spring Boot"))
				.andExpect(jsonPath("$.match.matchedDesiredSkills[0]").value("Docker"))
				.andExpect(jsonPath("$.match.missingDesiredSkills").isEmpty())
				.andExpect(jsonPath("$.match.criteria.length()").value(4))
				.andExpect(jsonPath("$.match.criteria[0].criterion").value("REQUIRED_SKILLS"))
				.andExpect(jsonPath("$.match.criteria[0].weight").value(45))
				.andExpect(jsonPath("$.match.criteria[0].points").value(22.5))
				.andExpect(jsonPath("$.match.criteria[0].explanation").isNotEmpty())
				.andExpect(jsonPath("$.match.summary").isNotEmpty());
	}

	@Test
	void scoreFollowsChangesInTheBaseResume() throws Exception {
		User student = createStudent("Recife, PE", "[]", "[\"REMOTE\"]", java);
		// Só obrigatórias 1/2 (22,5): sem desejável, sem interesse, modelo recusado e outra cidade -> 23.
		as(student, get(MATCH + jobId)).andExpect(jsonPath("$.match.score").value(23));

		putSkills(student, java, spring, docker);
		putProfileAndResume(student, "Garanhuns, PE", "[\"BACKEND\", \"DATA\"]", "[]");

		as(student, get(MATCH + jobId)).andExpect(jsonPath("$.match.score").value(100));
	}

	@Test
	void jobThatIsNotOpenOrDoesNotExistIsNotFound() throws Exception {
		User student = createStudent("Garanhuns, PE", "[]", "[]", java);
		as(company, post("/api/v1/jobs/" + jobId + "/close")).andExpect(status().isOk());

		as(student, get(MATCH + jobId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(student, get(MATCH + UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void studentWithoutProfileCannotBeMatched() throws Exception {
		as(createUser(UserRole.STUDENT), get(MATCH + jobId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_FOUND"));
	}

	@Test
	void onlyStudentsUseTheStudentScoreRoute() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.COMPANY, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			as(createUser(role), get(MATCH + jobId)).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(MATCH + jobId)).andExpect(status().isUnauthorized());
	}

	private User createStudent(String location, String interestAreas, String preferredWorkModels, UUID... skills)
			throws Exception {
		User student = createUser(UserRole.STUDENT);
		putProfileAndResume(student, location, interestAreas, preferredWorkModels);
		putSkills(student, skills);
		return student;
	}

	private void putProfileAndResume(User student, String location, String interestAreas,
			String preferredWorkModels) throws Exception {
		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Ana Souza\", \"location\": \"%s\"}".formatted(location)))
				.andExpect(status().isOk());
		as(student, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON)
				.content("{\"interestAreas\": %s, \"preferredWorkModels\": %s}"
						.formatted(interestAreas, preferredWorkModels)))
				.andExpect(status().isOk());
	}

	private void putSkills(User student, UUID... skills) throws Exception {
		String ids = Arrays.stream(skills).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
		as(student, put("/api/v1/students/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [" + ids + "]}"))
				.andExpect(status().isOk());
	}

	private UUID skillId(String normalizedName) {
		return jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = ?", UUID.class,
				normalizedName);
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}
}
