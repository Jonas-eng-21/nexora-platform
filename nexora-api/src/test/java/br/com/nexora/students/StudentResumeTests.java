package br.com.nexora.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class StudentResumeTests {

	private static final String RESUME_PATH = "/api/v1/students/me/resume";

	private static final String FULL_RESUME = """
			{
			  "education": [{"institution": "UFAPE", "course": "Ciência da Computação", "startYear": 2022, "endYear": 2026}],
			  "experiences": [{"company": "Tech Garanhuns", "role": "Estagiária backend",
			                   "description": "APIs REST em Java.", "startDate": "2025-03", "endDate": null}],
			  "projects": [{"name": "Nexora", "description": "Plataforma de vagas.", "url": "https://github.com/ana/nexora"}],
			  "courses": [{"name": "Spring Boot Essentials", "institution": "Alura", "year": 2024}],
			  "softSkills": ["Comunicação", "Trabalho em equipe"],
			  "interests": ["Backend", "Dados"],
			  "links": {"github": "https://github.com/ana", "linkedin": "https://linkedin.com/in/ana", "portfolio": null}
			}
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
	void resumeRequiresAnExistingProfile() throws Exception {
		User student = createUser(UserRole.STUDENT);

		putResume(student, FULL_RESUME)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_FOUND"));
		as(student, get(RESUME_PATH)).andExpect(status().isNotFound());
	}

	@Test
	void resumeStartsEmptyWithStableStructure() throws Exception {
		User student = createStudentWithProfile("Ana Souza");

		as(student, get(RESUME_PATH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.education").isEmpty())
				.andExpect(jsonPath("$.experiences").isEmpty())
				.andExpect(jsonPath("$.projects").isEmpty())
				.andExpect(jsonPath("$.courses").isEmpty())
				.andExpect(jsonPath("$.softSkills").isEmpty())
				.andExpect(jsonPath("$.interests").isEmpty())
				.andExpect(jsonPath("$.links").exists());
	}

	@Test
	void studentSavesAndReadsStructuredResume() throws Exception {
		User student = createStudentWithProfile("Ana Souza");

		putResume(student, FULL_RESUME).andExpect(status().isOk());
		// Garante que a leitura vem do banco, não da memória da requisição anterior.
		entityManager.flush();
		entityManager.clear();

		as(student, get(RESUME_PATH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.education[0].institution").value("UFAPE"))
				.andExpect(jsonPath("$.education[0].startYear").value(2022))
				.andExpect(jsonPath("$.experiences[0].role").value("Estagiária backend"))
				.andExpect(jsonPath("$.experiences[0].startDate").value("2025-03"))
				.andExpect(jsonPath("$.experiences[0].endDate").doesNotExist())
				.andExpect(jsonPath("$.projects[0].url").value("https://github.com/ana/nexora"))
				.andExpect(jsonPath("$.courses[0].year").value(2024))
				.andExpect(jsonPath("$.softSkills[1]").value("Trabalho em equipe"))
				.andExpect(jsonPath("$.interests[0]").value("Backend"))
				.andExpect(jsonPath("$.links.github").value("https://github.com/ana"));

		assertThat(jdbcTemplate.queryForObject(
				"SELECT resume -> 'projects' -> 0 ->> 'name' FROM students", String.class)).isEqualTo("Nexora");
	}

	@Test
	void savingReplacesTheWholeResume() throws Exception {
		User student = createStudentWithProfile("Ana Souza");
		putResume(student, FULL_RESUME).andExpect(status().isOk());

		putResume(student, """
				{"interests": ["Frontend"], "projects": []}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.interests.length()").value(1))
				.andExpect(jsonPath("$.interests[0]").value("Frontend"))
				.andExpect(jsonPath("$.projects").isEmpty())
				.andExpect(jsonPath("$.experiences").isEmpty());
	}

	@Test
	void updatingTheProfileKeepsTheResume() throws Exception {
		User student = createStudentWithProfile("Ana Souza");
		putResume(student, FULL_RESUME).andExpect(status().isOk());

		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Ana S. Souza\"}"))
				.andExpect(status().isOk());
		entityManager.flush();
		entityManager.clear();

		as(student, get(RESUME_PATH))
				.andExpect(jsonPath("$.projects[0].name").value("Nexora"))
				.andExpect(jsonPath("$.interests.length()").value(2));
	}

	@Test
	void invalidSectionsAreRejectedWithFieldPaths() throws Exception {
		User student = createStudentWithProfile("Ana Souza");

		putResume(student, """
				{
				  "experiences": [{"company": " ", "role": "Dev", "startDate": "03/2025"}],
				  "projects": [{"name": "Site", "url": "javascript:alert(1)"}],
				  "education": [{"institution": "UFAPE", "course": "CC", "startYear": 1800}],
				  "interests": ["ok", " "],
				  "links": {"github": "not-a-url"}
				}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("experiences[0].company")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("experiences[0].startDate")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("projects[0].url")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("education[0].startYear")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("interests[1]")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("links.github")));

		as(student, get(RESUME_PATH)).andExpect(jsonPath("$.experiences").isEmpty());
	}

	@Test
	void oversizedListsAreRejected() throws Exception {
		User student = createStudentWithProfile("Ana Souza");
		String manyInterests = "\"x\",".repeat(31);

		putResume(student, "{\"interests\": [" + manyInterests.substring(0, manyInterests.length() - 1) + "]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field", hasItem("interests")));
	}

	@Test
	void studentOnlyAffectsOwnResume() throws Exception {
		User ana = createStudentWithProfile("Ana Souza");
		User bruno = createStudentWithProfile("Bruno Lima");

		putResume(ana, FULL_RESUME).andExpect(status().isOk());

		as(bruno, get(RESUME_PATH)).andExpect(jsonPath("$.projects").isEmpty());
	}

	@Test
	void otherRolesCannotUseResumeRoutes() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.COMPANY, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			User user = createUser(role);

			as(user, get(RESUME_PATH)).andExpect(status().isForbidden());
			putResume(user, FULL_RESUME).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(RESUME_PATH)).andExpect(status().isUnauthorized());
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private User createStudentWithProfile(String fullName) throws Exception {
		User student = createUser(UserRole.STUDENT);
		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"%s\"}".formatted(fullName)))
				.andExpect(status().isOk());
		return student;
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}

	private ResultActions putResume(User user, String body) throws Exception {
		return as(user, put(RESUME_PATH).contentType(MediaType.APPLICATION_JSON).content(body));
	}
}
