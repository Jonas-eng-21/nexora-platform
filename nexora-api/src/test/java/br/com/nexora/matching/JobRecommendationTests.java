package br.com.nexora.matching;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.jayway.jsonpath.JsonPath;

import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class JobRecommendationTests {

	private static final String RECOMMENDED = "/api/v1/matching/jobs";

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
	private UUID python;
	private UUID docker;
	private User company;
	private User student;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		java = skillId("java");
		python = skillId("python");
		docker = skillId("docker");
		company = createCompany("Tech Garanhuns");
		student = createUser(UserRole.STUDENT);
		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Ana Souza\", \"location\": \"Garanhuns, PE\"}"))
				.andExpect(status().isOk());
		as(student, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON)
				.content("{\"interestAreas\": [\"BACKEND\"]}")).andExpect(status().isOk());
		as(student, put("/api/v1/students/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [\"%s\", \"%s\"]}".formatted(java, docker))).andExpect(status().isOk());
	}

	@Test
	void studentSeesOpenJobsOrderedByScoreWithJustification() throws Exception {
		// Publicadas da menos para a mais aderente, para a ordem do resultado não coincidir com a de criação.
		String weak = publishJob(company, "Analista de Dados", "DATA", "ON_SITE", "Recife, PE", python, null);
		String medium = publishJob(company, "Backend Python", "BACKEND", "REMOTE", null, python, docker);
		String strong = publishJob(company, "Estágio Backend Java", "BACKEND", "REMOTE", null, java, docker);

		as(student, get(RECOMMENDED))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].job.id").value(strong))
				.andExpect(jsonPath("$.content[0].job.title").value("Estágio Backend Java"))
				.andExpect(jsonPath("$.content[0].job.companyName").value("Tech Garanhuns"))
				.andExpect(jsonPath("$.content[0].match.score").value(100))
				.andExpect(jsonPath("$.content[0].match.matchedRequiredSkills[0]").value("Java"))
				.andExpect(jsonPath("$.content[0].match.summary").isNotEmpty())
				.andExpect(jsonPath("$.content[0].match.criteria.length()").value(4))
				// Obrigatórias 0 + desejáveis 20 + interesse 20 + remoto e sem preferência 15 = 55.
				.andExpect(jsonPath("$.content[1].job.id").value(medium))
				.andExpect(jsonPath("$.content[1].match.score").value(55))
				.andExpect(jsonPath("$.content[1].match.missingRequiredSkills[0]").value("Python"))
				// Só a metade "sem preferência de modelo" (7,5) de 80 pontos aplicáveis = 9.
				.andExpect(jsonPath("$.content[2].job.id").value(weak))
				.andExpect(jsonPath("$.content[2].match.score").value(9));
	}

	@Test
	void onlyOpenJobsFromActiveCompaniesAreRecommended() throws Exception {
		String open = publishJob(company, "Vaga aberta", "BACKEND", "REMOTE", null, java, null);
		String closed = publishJob(company, "Vaga encerrada", "BACKEND", "REMOTE", null, java, null);
		as(company, post("/api/v1/jobs/" + closed + "/close")).andExpect(status().isOk());
		String deleted = publishJob(company, "Vaga excluída", "BACKEND", "REMOTE", null, java, null);
		as(company, delete("/api/v1/jobs/" + deleted)).andExpect(status().isNoContent());
		User suspendedCompany = createCompany("Empresa Suspensa");
		publishJob(suspendedCompany, "Vaga de empresa suspensa", "BACKEND", "REMOTE", null, java, null);
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspendedCompany.getId());
		entityManager.clear();

		as(student, get(RECOMMENDED))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].job.id").value(open));
	}

	@Test
	void recommendationsArePaginated() throws Exception {
		String strong = publishJob(company, "Estágio Backend Java", "BACKEND", "REMOTE", null, java, docker);
		String medium = publishJob(company, "Backend Python", "BACKEND", "REMOTE", null, python, docker);
		String weak = publishJob(company, "Analista de Dados", "DATA", "ON_SITE", "Recife, PE", python, null);

		as(student, get(RECOMMENDED).param("size", "2"))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content[0].job.id").value(strong))
				.andExpect(jsonPath("$.content[1].job.id").value(medium));
		as(student, get(RECOMMENDED).param("size", "2").param("page", "1"))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].job.id").value(weak));
		as(student, get(RECOMMENDED).param("size", "2").param("page", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void jobsWithTheSameScoreKeepAStableOrderNewestFirst() throws Exception {
		String older = publishJob(company, "Backend A", "BACKEND", "REMOTE", null, java, null);
		String newer = publishJob(company, "Backend B", "BACKEND", "REMOTE", null, java, null);

		for (int attempt = 0; attempt < 3; attempt++) {
			as(student, get(RECOMMENDED))
					.andExpect(jsonPath("$.content[0].match.score").value(100))
					.andExpect(jsonPath("$.content[1].match.score").value(100))
					.andExpect(jsonPath("$.content[0].job.id").value(newer))
					.andExpect(jsonPath("$.content[1].job.id").value(older));
		}
	}

	@Test
	void recommendationsUseOnlyTheRequestingStudentsResume() throws Exception {
		publishJob(company, "Analista de Dados", "DATA", "REMOTE", null, python, null);
		User dataStudent = createUser(UserRole.STUDENT);
		as(dataStudent, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Bruno Lima\"}")).andExpect(status().isOk());
		as(dataStudent, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON)
				.content("{\"interestAreas\": [\"DATA\"]}")).andExpect(status().isOk());
		as(dataStudent, put("/api/v1/students/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [\"%s\"]}".formatted(python))).andExpect(status().isOk());

		as(dataStudent, get(RECOMMENDED)).andExpect(jsonPath("$.content[0].match.score").value(100));
		// Ana (Java, Docker, interesse em Backend) só pontua por vaga remota e ausência de preferência: 15 de 80.
		as(student, get(RECOMMENDED)).andExpect(jsonPath("$.content[0].match.score").value(19));
	}

	@Test
	void recommendationsRequireStudentProfileAndStudentRole() throws Exception {
		as(createUser(UserRole.STUDENT), get(RECOMMENDED))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_FOUND"));
		for (UserRole role : new UserRole[] { UserRole.COMPANY, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			as(createUser(role), get(RECOMMENDED)).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(RECOMMENDED)).andExpect(status().isUnauthorized());
	}

	private String publishJob(User owner, String title, String area, String workModel, String location,
			UUID requiredSkill, UUID desiredSkill) throws Exception {
		String locationJson = location == null ? "null" : "\"" + location + "\"";
		String desired = desiredSkill == null ? "[]" : "[\"%s\"]".formatted(desiredSkill);
		String response = as(owner, post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "%s", "description": "Descrição da vaga.", "area": "%s", "location": %s,
				 "workModel": "%s", "level": "INTERNSHIP", "requiredSkillIds": ["%s"], "desiredSkillIds": %s}
				""".formatted(title, area, locationJson, workModel, requiredSkill, desired)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private UUID skillId(String normalizedName) {
		return jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = ?", UUID.class,
				normalizedName);
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private User createCompany(String name) throws Exception {
		User owner = createUser(UserRole.COMPANY);
		as(owner, put("/api/v1/companies/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"%s\"}".formatted(name)))
				.andExpect(status().isOk());
		return owner;
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}
}
