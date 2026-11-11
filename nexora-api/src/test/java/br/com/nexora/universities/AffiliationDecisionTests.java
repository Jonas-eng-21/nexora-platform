package br.com.nexora.universities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
class AffiliationDecisionTests {

	private static final String AFFILIATIONS = "/api/v1/affiliations";
	private static final String RECEIVED = "/api/v1/affiliations/received";
	private static final String ADMIN = "/api/v1/admin/affiliations";
	private static final String REASON = "{\"reason\": \"Matrícula não localizada\"}";

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
	private User university;
	private UUID universityId;
	private User student;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		university = createUser(UserRole.UNIVERSITY);
		universityId = createUniversityFor(university, "UFAPE");
		student = createStudentWithProfile("Ana Souza");
	}

	@Test
	void universityListsOnlyRequestsAddressedToIt() throws Exception {
		UUID mine = requestAffiliation(student, universityId);
		User otherUniversity = createUser(UserRole.UNIVERSITY);
		UUID otherUniversityId = createUniversityFor(otherUniversity, "Outra");
		requestAffiliation(createStudentWithProfile("Bruno Lima"), otherUniversityId);

		as(university, get(RECEIVED))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(mine.toString()))
				.andExpect(jsonPath("$.content[0].status").value("PENDING"))
				.andExpect(jsonPath("$.content[0].student.fullName").value("Ana Souza"))
				.andExpect(jsonPath("$.content[0].student.id").isNotEmpty())
				.andExpect(jsonPath("$.content[0].requestedAt").isNotEmpty());
		as(university, get(RECEIVED).param("status", "APPROVED"))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void universityApprovesRequestAndDecisionIsRecorded() throws Exception {
		UUID affiliation = requestAffiliation(student, universityId);

		as(university, post(AFFILIATIONS + "/" + affiliation + "/approve")
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.decidedAt").isNotEmpty());

		Map<String, Object> row = affiliationRow(affiliation);
		assertThat(row.get("status")).isEqualTo("APPROVED");
		assertThat(row.get("decided_by")).isEqualTo(university.getId());
		assertThat(row.get("decided_at")).isNotNull();
		as(student, get(AFFILIATIONS + "/me"))
				.andExpect(jsonPath("$[0].status").value("APPROVED"))
				.andExpect(jsonPath("$[0].decidedAt").isNotEmpty());
	}

	@Test
	void universityRejectsWithReasonAndStudentCanRequestAgain() throws Exception {
		UUID affiliation = requestAffiliation(student, universityId);

		as(university, post(AFFILIATIONS + "/" + affiliation + "/reject")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		assertThat(affiliationRow(affiliation).get("status")).isEqualTo("PENDING");

		as(university, post(AFFILIATIONS + "/" + affiliation + "/reject")
				.contentType(MediaType.APPLICATION_JSON).content(REASON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.decisionReason").value("Matrícula não localizada"));

		Map<String, Object> row = affiliationRow(affiliation);
		assertThat(row.get("decided_by")).isEqualTo(university.getId());
		assertThat(row.get("decision_reason")).isEqualTo("Matrícula não localizada");
		as(student, get(AFFILIATIONS + "/me"))
				.andExpect(jsonPath("$[0].status").value("REJECTED"))
				.andExpect(jsonPath("$[0].decisionReason").value("Matrícula não localizada"));
		requestAffiliation(student, universityId);
		assertThat(affiliationCount()).isEqualTo(2);
	}

	@Test
	void anotherUniversityCannotSeeOrDecideTheRequest() throws Exception {
		UUID affiliation = requestAffiliation(student, universityId);
		User intruder = createUser(UserRole.UNIVERSITY);
		createUniversityFor(intruder, "Intrusa");

		as(intruder, get(RECEIVED)).andExpect(jsonPath("$.totalElements").value(0));
		as(intruder, post(AFFILIATIONS + "/" + affiliation + "/approve")
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("AFFILIATION_NOT_FOUND"));
		as(intruder, post(AFFILIATIONS + "/" + affiliation + "/reject")
				.contentType(MediaType.APPLICATION_JSON).content(REASON))
				.andExpect(status().isNotFound());

		assertThat(affiliationRow(affiliation).get("status")).isEqualTo("PENDING");
		assertThat(affiliationRow(affiliation).get("decided_by")).isNull();
	}

	@Test
	void studentAndCompanyCannotDecide() throws Exception {
		UUID affiliation = requestAffiliation(student, universityId);

		for (User user : new User[] { student, createUser(UserRole.COMPANY) }) {
			as(user, get(RECEIVED)).andExpect(status().isForbidden());
			as(user, post(AFFILIATIONS + "/" + affiliation + "/approve")
					.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
			as(user, post(AFFILIATIONS + "/" + affiliation + "/reject")
					.contentType(MediaType.APPLICATION_JSON).content(REASON)).andExpect(status().isForbidden());
			as(user, post(ADMIN + "/" + affiliation + "/approve")
					.contentType(MediaType.APPLICATION_JSON).content(REASON)).andExpect(status().isForbidden());
		}
		mockMvc.perform(post(AFFILIATIONS + "/" + affiliation + "/approve")
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

		assertThat(affiliationRow(affiliation).get("status")).isEqualTo("PENDING");
	}

	@Test
	void requestCannotBeDecidedTwiceAndUnknownIs404() throws Exception {
		UUID affiliation = requestAffiliation(student, universityId);
		as(university, post(AFFILIATIONS + "/" + affiliation + "/approve")
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

		as(university, post(AFFILIATIONS + "/" + affiliation + "/reject")
				.contentType(MediaType.APPLICATION_JSON).content(REASON))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AFFILIATION_ALREADY_DECIDED"));
		as(university, post(AFFILIATIONS + "/" + affiliation + "/approve")
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
		as(university, post(AFFILIATIONS + "/" + UUID.randomUUID() + "/approve")
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("AFFILIATION_NOT_FOUND"));

		assertThat(affiliationRow(affiliation).get("status")).isEqualTo("APPROVED");
	}

	@Test
	void adminDecidesAnyRequestWithAudit() throws Exception {
		User admin = createUser(UserRole.ADMIN);
		UUID toApprove = requestAffiliation(student, universityId);
		UUID toReject = requestAffiliation(createStudentWithProfile("Bruno Lima"), universityId);

		as(admin, get(ADMIN).param("status", "PENDING"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].university.name").value("UFAPE"));
		as(admin, post(ADMIN + "/" + toApprove + "/approve").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		as(admin, post(ADMIN + "/" + toApprove + "/approve").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Universidade indisponível\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));
		as(admin, post(ADMIN + "/" + toReject + "/reject").contentType(MediaType.APPLICATION_JSON).content(REASON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		assertThat(affiliationRow(toApprove).get("decided_by")).isEqualTo(admin.getId());
		assertThat(affiliationRow(toReject).get("decision_reason")).isEqualTo("Matrícula não localizada");
		Map<String, Object> approvedLog = jdbcTemplate.queryForMap(
				"SELECT * FROM admin_audit_logs WHERE target_id = ?", toApprove);
		assertThat(approvedLog.get("admin_id")).isEqualTo(admin.getId());
		assertThat(approvedLog.get("action")).isEqualTo("AFFILIATION_APPROVED");
		assertThat(approvedLog.get("target_type")).isEqualTo("AFFILIATION");
		assertThat(approvedLog.get("reason")).isEqualTo("Universidade indisponível");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT action FROM admin_audit_logs WHERE target_id = ?", String.class, toReject))
				.isEqualTo("AFFILIATION_REJECTED");
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private UUID createUniversityFor(User account, String name) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, ?, 'APPROVED') RETURNING id",
				UUID.class, account.getId(), name);
	}

	private User createStudentWithProfile(String fullName) throws Exception {
		User newStudent = createUser(UserRole.STUDENT);
		as(newStudent, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"%s\"}".formatted(fullName)))
				.andExpect(status().isOk());
		return newStudent;
	}

	private UUID requestAffiliation(User requester, UUID targetUniversityId) throws Exception {
		String body = as(requester, post(AFFILIATIONS).contentType(MediaType.APPLICATION_JSON)
				.content("{\"universityId\": \"%s\"}".formatted(targetUniversityId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}

	/** Teste e requisição compartilham a transação: sincroniza antes de ler via JDBC. */
	private Map<String, Object> affiliationRow(UUID affiliationId) {
		entityManager.flush();
		return jdbcTemplate.queryForMap("SELECT * FROM university_affiliations WHERE id = ?", affiliationId);
	}

	private int affiliationCount() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM university_affiliations", Integer.class);
	}
}
