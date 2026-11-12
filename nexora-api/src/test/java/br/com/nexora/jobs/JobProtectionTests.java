package br.com.nexora.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import br.com.nexora.jobs.service.JobService;
import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

/** US-23: propriedade e estado da vaga — só a dona ativa opera, e só vaga aberta de empresa ativa aceita candidatura. */
@SpringBootTest
@Transactional
class JobProtectionTests {

	private static final String JOBS = "/api/v1/jobs";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JobService jobService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private MockMvc mockMvc;
	private User owner;
	private UUID jobId;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		owner = createCompany("Tech Garanhuns");
		jobId = publishJob(owner);
	}

	@Test
	void anotherCompanyCannotOperateOnTheJobThroughAnyRoute() throws Exception {
		User intruder = createCompany("Empresa Intrusa");

		expectJobNotFound(as(intruder, put(JOBS + "/" + jobId).contentType(MediaType.APPLICATION_JSON)
				.content(jobPayload("Vaga sequestrada"))));
		expectJobNotFound(as(intruder, post(JOBS + "/" + jobId + "/close")));
		expectJobNotFound(as(intruder, post(JOBS + "/" + jobId + "/reopen")));
		expectJobNotFound(as(intruder, delete(JOBS + "/" + jobId)));
		as(intruder, get(JOBS + "/mine")).andExpect(jsonPath("$.totalElements").value(0));

		assertJobUntouched();
	}

	@Test
	void nonCompanyRolesCannotOperateOnJobs() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.UNIVERSITY }) {
			User user = createUser(role);

			as(user, post(JOBS).contentType(MediaType.APPLICATION_JSON).content(jobPayload("Outra vaga")))
					.andExpect(status().isForbidden());
			as(user, put(JOBS + "/" + jobId).contentType(MediaType.APPLICATION_JSON)
					.content(jobPayload("Vaga alterada"))).andExpect(status().isForbidden());
			as(user, post(JOBS + "/" + jobId + "/close")).andExpect(status().isForbidden());
			as(user, post(JOBS + "/" + jobId + "/reopen")).andExpect(status().isForbidden());
			as(user, delete(JOBS + "/" + jobId)).andExpect(status().isForbidden());
			as(user, get(JOBS + "/mine")).andExpect(status().isForbidden());
		}

		assertJobUntouched();
	}

	@Test
	void suspendedCompanyCannotOperateEvenWithValidToken() throws Exception {
		String token = tokenService.issue(owner);
		setOwnerStatus("SUSPENDED");

		for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
				post(JOBS).contentType(MediaType.APPLICATION_JSON).content(jobPayload("Nova vaga")),
				put(JOBS + "/" + jobId).contentType(MediaType.APPLICATION_JSON).content(jobPayload("Vaga alterada")),
				post(JOBS + "/" + jobId + "/close"),
				post(JOBS + "/" + jobId + "/reopen"),
				delete(JOBS + "/" + jobId),
				get(JOBS + "/mine") }) {
			mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
		}

		assertJobUntouched();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM jobs", Integer.class)).isEqualTo(1);
	}

	@Test
	void jobOfSuspendedCompanyIsHiddenFromStudentsUntilReactivation() throws Exception {
		User student = createUser(UserRole.STUDENT);
		as(student, get(JOBS + "/" + jobId)).andExpect(status().isOk());

		setOwnerStatus("SUSPENDED");
		as(student, get(JOBS)).andExpect(jsonPath("$.totalElements").value(0));
		expectJobNotFound(as(student, get(JOBS + "/" + jobId)));

		setOwnerStatus("ACTIVE");
		as(student, get(JOBS)).andExpect(jsonPath("$.totalElements").value(1));
		as(student, get(JOBS + "/" + jobId)).andExpect(status().isOk());
	}

	@Test
	void openJobOfActiveCompanyAcceptsApplications() {
		assertThat(jobService.requireAcceptingApplications(jobId).getId()).isEqualTo(jobId);
	}

	@Test
	void closedJobDoesNotAcceptApplicationsUntilReopened() throws Exception {
		as(owner, post(JOBS + "/" + jobId + "/close")).andExpect(status().isOk());
		assertNotAcceptingApplications();

		as(owner, post(JOBS + "/" + jobId + "/reopen")).andExpect(status().isOk());
		assertThat(jobService.requireAcceptingApplications(jobId).getId()).isEqualTo(jobId);
	}

	@Test
	void jobOfInactiveCompanyDoesNotAcceptApplications() {
		for (String status : new String[] { "SUSPENDED", "REJECTED", "PENDING_APPROVAL" }) {
			setOwnerStatus(status);
			assertNotAcceptingApplications();
		}

		setOwnerStatus("ACTIVE");
		assertThat(jobService.requireAcceptingApplications(jobId).getId()).isEqualTo(jobId);
	}

	@Test
	void deletedOrUnknownJobIsNotFoundForApplications() throws Exception {
		as(owner, delete(JOBS + "/" + jobId)).andExpect(status().isNoContent());
		entityManager.flush();

		for (UUID id : new UUID[] { jobId, UUID.randomUUID() }) {
			assertThatThrownBy(() -> jobService.requireAcceptingApplications(id))
					.isInstanceOfSatisfying(ApiException.class, ex -> {
						assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
						assertThat(ex.getCode()).isEqualTo("JOB_NOT_FOUND");
					});
		}
	}

	private void assertNotAcceptingApplications() {
		assertThatThrownBy(() -> jobService.requireAcceptingApplications(jobId))
				.isInstanceOfSatisfying(ApiException.class, ex -> {
					assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
					assertThat(ex.getCode()).isEqualTo("JOB_NOT_ACCEPTING_APPLICATIONS");
				});
	}

	private static void expectJobNotFound(ResultActions result) throws Exception {
		result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
	}

	/** A vaga continua exatamente como foi publicada pela dona. */
	private void assertJobUntouched() {
		entityManager.flush();
		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM jobs WHERE id = ?", jobId);
		assertThat(row.get("title")).isEqualTo("Estágio em Backend");
		assertThat(row.get("status")).isEqualTo("OPEN");
		assertThat(row.get("deleted_at")).isNull();
	}

	private void setOwnerStatus(String status) {
		entityManager.flush();
		jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status, owner.getId());
		entityManager.clear();
	}

	private String jobPayload(String title) {
		UUID java = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = 'java'", UUID.class);
		return """
				{"title": "%s", "description": "Desenvolver APIs REST.", "area": "BACKEND",
				 "workModel": "HYBRID", "level": "INTERNSHIP", "requiredSkillIds": ["%s"]}
				""".formatted(title, java);
	}

	private UUID publishJob(User company) throws Exception {
		String response = as(company, post(JOBS).contentType(MediaType.APPLICATION_JSON)
				.content(jobPayload("Estágio em Backend")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(response, "$.id"));
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
