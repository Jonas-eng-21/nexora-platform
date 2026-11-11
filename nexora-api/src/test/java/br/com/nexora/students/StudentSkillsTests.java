package br.com.nexora.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class StudentSkillsTests {

	private static final String SKILLS_PATH = "/api/v1/students/me/skills";

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
	void skillsRequireAnExistingProfile() throws Exception {
		User student = createUser(UserRole.STUDENT);

		putSkills(student, skillId("Java"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_FOUND"));
	}

	@Test
	void studentSelectsSkillsFromCatalog() throws Exception {
		User student = createStudentWithProfile();
		as(student, get(SKILLS_PATH)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

		putSkills(student, skillId("Java"), skillId("Docker"), skillId("Java"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name", containsInAnyOrder("Docker", "Java")));
		entityManager.flush();
		entityManager.clear();

		as(student, get(SKILLS_PATH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Docker"))
				.andExpect(jsonPath("$[1].name").value("Java"))
				.andExpect(jsonPath("$[0].id").isNotEmpty());
		assertThat(studentSkillRows()).isEqualTo(2);
	}

	@Test
	void savingReplacesThePreviousSelection() throws Exception {
		User student = createStudentWithProfile();
		putSkills(student, skillId("Java"), skillId("Docker")).andExpect(status().isOk());

		putSkills(student, skillId("Python"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("Python"));
		putSkills(student).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

		assertThat(studentSkillRows()).isZero();
	}

	@Test
	void unknownSkillIsRejectedWithoutChangingTheSelection() throws Exception {
		User student = createStudentWithProfile();
		putSkills(student, skillId("Java")).andExpect(status().isOk());

		putSkills(student, skillId("Docker"), UUID.randomUUID())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SKILL_NOT_FOUND"));

		as(student, get(SKILLS_PATH))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("Java"));
	}

	@Test
	void studentOnlyAffectsOwnSkills() throws Exception {
		User ana = createStudentWithProfile();
		User bruno = createStudentWithProfile();
		putSkills(bruno, skillId("Python")).andExpect(status().isOk());

		putSkills(ana, skillId("Java")).andExpect(status().isOk());

		as(bruno, get(SKILLS_PATH))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("Python"));
	}

	@Test
	void invalidPayloadIsRejected() throws Exception {
		User student = createStudentWithProfile();

		as(student, put(SKILLS_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void otherRolesCannotUseStudentSkillRoutes() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.COMPANY, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			User user = createUser(role);

			as(user, get(SKILLS_PATH)).andExpect(status().isForbidden());
			putSkills(user, skillId("Java")).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(SKILLS_PATH)).andExpect(status().isUnauthorized());
	}

	private UUID skillId(String name) {
		return jdbcTemplate.queryForObject("SELECT id FROM skills WHERE name = ?", UUID.class, name);
	}

	private int studentSkillRows() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM student_skills", Integer.class);
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private User createStudentWithProfile() throws Exception {
		User student = createUser(UserRole.STUDENT);
		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Estudante Teste\"}"))
				.andExpect(status().isOk());
		return student;
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}

	private ResultActions putSkills(User user, UUID... skillIds) throws Exception {
		String ids = Arrays.stream(skillIds).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
		return as(user, put(SKILLS_PATH).contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [" + ids + "]}"));
	}
}
