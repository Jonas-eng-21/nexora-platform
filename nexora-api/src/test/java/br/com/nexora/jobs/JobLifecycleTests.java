package br.com.nexora.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class JobLifecycleTests {

	private static final String JOBS = "/api/v1/jobs";
	private static final String ADMIN_JOBS = "/api/v1/admin/jobs";

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
	private UUID postgres;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		java = skillId("java");
		spring = skillId("spring boot");
		docker = skillId("docker");
		postgres = skillId("postgresql");
	}

	@Test
	void companyUpdatesOwnJobDetailsAndSkills() throws Exception {
		User company = createCompany("Tech Garanhuns");
		String jobId = publishJob(company);

		as(company, put(JOBS + "/" + jobId).contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "  Desenvolvedor Backend Pleno ", "description": "Arquitetura e APIs.",
				 "area": "FULLSTACK", "location": " ", "workModel": "REMOTE",
				 "level": "MID", "status": "OPEN",
				 "requiredSkillIds": ["%s", "%s"], "desiredSkillIds": []}
				""".formatted(java, postgres)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(jobId))
				.andExpect(jsonPath("$.title").value("Desenvolvedor Backend Pleno"))
				.andExpect(jsonPath("$.description").value("Arquitetura e APIs."))
				.andExpect(jsonPath("$.area").value("FULLSTACK"))
				.andExpect(jsonPath("$.location").doesNotExist())
				.andExpect(jsonPath("$.workModel").value("REMOTE"))
				.andExpect(jsonPath("$.level").value("MID"))
				.andExpect(jsonPath("$.requiredSkills.length()").value(2))
				.andExpect(jsonPath("$.requiredSkills[0].name").value("Java"))
				.andExpect(jsonPath("$.requiredSkills[1].name").value("PostgreSQL"))
				.andExpect(jsonPath("$.desiredSkills.length()").value(0));

		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM job_skills WHERE job_id = ?", Integer.class,
				UUID.fromString(jobId))).isEqualTo(2);
	}

	@Test
	void companyClosesReopensAndListsOwnJobs() throws Exception {
		User company = createCompany("Tech Garanhuns");
		String jobId = publishJob(company);

		as(company, post(JOBS + "/" + jobId + "/close"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"));

		as(company, get(JOBS + "/mine").param("status", "CLOSED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(jobId));
		as(company, get(JOBS + "/mine").param("status", "OPEN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0));

		as(company, post(JOBS + "/" + jobId + "/reopen"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"));

		as(company, get(JOBS + "/mine"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].status").value("OPEN"));
	}

	@Test
	void softDeletePreservesDatabaseRowAndHidesJobFromOperations() throws Exception {
		User company = createCompany("Tech Garanhuns");
		String jobId = publishJob(company);

		as(company, delete(JOBS + "/" + jobId))
				.andExpect(status().isNoContent());

		// A linha permanece no banco com deleted_at preenchido e status CLOSED para não perder histórico/candidaturas.
		entityManager.flush();
		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT status, deleted_at FROM jobs WHERE id = ?",
				UUID.fromString(jobId));
		assertThat(row.get("status")).isEqualTo("CLOSED");
		assertThat(row.get("deleted_at")).isNotNull();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM job_skills WHERE job_id = ?", Integer.class,
				UUID.fromString(jobId))).isEqualTo(3);

		// Vaga excluída logicamente não aparece na listagem nem aceita novas operações.
		as(company, get(JOBS + "/mine"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0));
		as(company, put(JOBS + "/" + jobId).contentType(MediaType.APPLICATION_JSON).content(validJob()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(company, post(JOBS + "/" + jobId + "/close"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(company, post(JOBS + "/" + jobId + "/reopen"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(company, delete(JOBS + "/" + jobId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
	}

	@Test
	void companyCannotModifyOrDeleteAnotherCompanysJob() throws Exception {
		User owner = createCompany("Owner");
		User intruder = createCompany("Intruder");
		String jobId = publishJob(owner);

		as(intruder, put(JOBS + "/" + jobId).contentType(MediaType.APPLICATION_JSON).content(validJob()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(intruder, post(JOBS + "/" + jobId + "/close"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(intruder, post(JOBS + "/" + jobId + "/reopen"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		as(intruder, delete(JOBS + "/" + jobId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));

		as(owner, get(JOBS + "/mine"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].title").value("Estágio em Backend Java"));
	}

	@Test
	void adminIntervenesOnJobLifecycleWithAudit() throws Exception {
		User admin = createUser(UserRole.ADMIN);
		User company = createCompany("Tech Garanhuns");
		String jobId = publishJob(company);

		as(admin, put(ADMIN_JOBS + "/" + jobId).contentType(MediaType.APPLICATION_JSON).content("""
				{"job": %s, "reason": "Ajuste moderado no título"}
				""".formatted(validJob().replace("Estágio em Backend Java", "Estágio Backend Revisado"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Estágio Backend Revisado"));

		as(admin, post(ADMIN_JOBS + "/" + jobId + "/close").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Vaga suspensa para revisão\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"));

		as(admin, post(ADMIN_JOBS + "/" + jobId + "/reopen").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Revisão concluída\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"));

		as(admin, delete(ADMIN_JOBS + "/" + jobId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Vaga fraudulenta\"}"))
				.andExpect(status().isNoContent());

		entityManager.flush();
		List<Map<String, Object>> logs = jdbcTemplate.queryForList(
				"SELECT action, target_type, target_id, reason FROM admin_audit_logs ORDER BY created_at, id");
		assertThat(logs).hasSize(4);
		assertThat(logs).extracting(row -> row.get("action"))
				.containsExactly("JOB_UPDATED", "JOB_CLOSED", "JOB_REOPENED", "JOB_DELETED");
		assertThat(logs).allSatisfy(row -> {
			assertThat(row.get("target_type")).isEqualTo("JOB");
			assertThat(row.get("target_id")).isEqualTo(UUID.fromString(jobId));
		});
	}

	@Test
	void adminInterventionRequiresReasonAndNonAdminsAreForbidden() throws Exception {
		User admin = createUser(UserRole.ADMIN);
		User company = createCompany("Tech Garanhuns");
		String jobId = publishJob(company);

		as(admin, post(ADMIN_JOBS + "/" + jobId + "/close").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		as(company, delete(ADMIN_JOBS + "/" + jobId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Tentativa\"}"))
				.andExpect(status().isForbidden());

		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_audit_logs", Integer.class)).isZero();
	}

	private String validJob() {
		return """
				{"title": "Estágio em Backend Java", "description": "Desenvolver APIs REST.",
				 "area": "BACKEND", "location": "Garanhuns, PE", "workModel": "HYBRID",
				 "level": "INTERNSHIP", "requiredSkillIds": ["%s", "%s"], "desiredSkillIds": ["%s"]}
				""".formatted(java, spring, docker);
	}

	private String publishJob(User company) throws Exception {
		String response = as(company, post(JOBS).contentType(MediaType.APPLICATION_JSON).content(validJob()))
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

