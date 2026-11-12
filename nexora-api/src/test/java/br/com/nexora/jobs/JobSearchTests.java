package br.com.nexora.jobs;

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
class JobSearchTests {

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
	private UUID python;
	private UUID docker;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		java = skillId("java");
		python = skillId("python");
		docker = skillId("docker");
	}

	@Test
	void studentListsOnlyOpenNonDeletedJobsFromActiveCompanies() throws Exception {
		User student = createUser(UserRole.STUDENT);
		User activeCompany = createCompany("Tech Garanhuns");
		User suspendedCompany = createCompany("Empresa Suspensa");

		String openJobId = publishJob(activeCompany, "Estágio Java", "Backend", "Garanhuns, PE",
				"HYBRID", "INTERNSHIP", "OPEN", java, docker);
		publishJob(activeCompany, "Vaga Fechada", "Backend", "Garanhuns, PE",
				"REMOTE", "JUNIOR", "CLOSED", java, null);
		String deletedJobId = publishJob(activeCompany, "Vaga Excluída", "Backend", "Garanhuns, PE",
				"ON_SITE", "JUNIOR", "OPEN", java, null);
		as(activeCompany, delete(JOBS + "/" + deletedJobId)).andExpect(status().isNoContent());

		publishJob(suspendedCompany, "Vaga de Suspensa", "Backend", "Recife, PE",
				"REMOTE", "JUNIOR", "OPEN", java, null);
		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspendedCompany.getId());
		entityManager.clear();

		as(student, get(JOBS))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(openJobId))
				.andExpect(jsonPath("$.content[0].companyName").value("Tech Garanhuns"))
				.andExpect(jsonPath("$.content[0].requiredSkills[0].name").value("Java"))
				.andExpect(jsonPath("$.content[0].desiredSkills[0].name").value("Docker"));
	}

	@Test
	void filtersBySearchAreaLocationWorkModelLevelAndSkill() throws Exception {
		User student = createUser(UserRole.STUDENT);
		User company = createCompany("Agreste Digital");

		String backendId = publishJob(company, "Estágio Backend Java", "Desenvolvimento Backend", "Garanhuns, PE",
				"HYBRID", "INTERNSHIP", "OPEN", java, docker);
		String dataId = publishJob(company, "Analista de Dados Júnior", "Ciência de Dados", "Recife, PE",
				"REMOTE", "JUNIOR", "OPEN", python, null);

		as(student, get(JOBS).param("search", "java"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(backendId));

		as(student, get(JOBS).param("area", "ciência de dados"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(dataId));

		as(student, get(JOBS).param("location", "garanhuns"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(backendId));

		as(student, get(JOBS).param("workModel", "REMOTE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(dataId));

		as(student, get(JOBS).param("level", "INTERNSHIP"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(backendId));

		as(student, get(JOBS).param("skillId", docker.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(backendId));
	}

	@Test
	void getJobDetailShowsOpenJobAndHidesClosedOrDeletedFromStudents() throws Exception {
		User student = createUser(UserRole.STUDENT);
		User company = createCompany("Tech Garanhuns");
		User admin = createUser(UserRole.ADMIN);

		String jobId = publishJob(company, "Estágio Java", "Backend", "Garanhuns, PE",
				"HYBRID", "INTERNSHIP", "OPEN", java, docker);

		as(student, get(JOBS + "/" + jobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(jobId))
				.andExpect(jsonPath("$.companyName").value("Tech Garanhuns"))
				.andExpect(jsonPath("$.requiredSkills.length()").value(1))
				.andExpect(jsonPath("$.desiredSkills.length()").value(1));

		// Quando encerrada, o estudante não consulta, mas a empresa dona e o admin ainda consultam.
		as(company, post(JOBS + "/" + jobId + "/close")).andExpect(status().isOk());
		as(student, get(JOBS + "/" + jobId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(company, get(JOBS + "/" + jobId)).andExpect(status().isOk());
		as(admin, get(JOBS + "/" + jobId)).andExpect(status().isOk());

		// Quando excluída logicamente, fica oculta para todos na consulta de vagas.
		as(company, delete(JOBS + "/" + jobId)).andExpect(status().isNoContent());
		as(company, get(JOBS + "/" + jobId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(admin, get(JOBS + "/" + jobId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
	}

	@Test
	void unauthenticatedRequestsAreRejected() throws Exception {
		mockMvc.perform(get(JOBS)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(JOBS + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

	private String publishJob(User company, String title, String area, String location, String workModel,
			String level, String status, UUID requiredSkill, UUID desiredSkill) throws Exception {
		String desired = desiredSkill == null ? "[]" : "[\"%s\"]".formatted(desiredSkill);
		String response = as(company, post(JOBS).contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "%s", "description": "Descrição da vaga.", "area": "%s", "location": "%s",
				 "workModel": "%s", "level": "%s", "status": "%s",
				 "requiredSkillIds": ["%s"], "desiredSkillIds": %s}
				""".formatted(title, area, location, workModel, level, status, requiredSkill, desired)))
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
		User company = createUser(UserRole.COMPANY);
		as(company, put("/api/v1/companies/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"%s\"}".formatted(name)))
				.andExpect(status().isOk());
		return company;
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}
}

