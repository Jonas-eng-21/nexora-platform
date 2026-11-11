package br.com.nexora.universities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import org.springframework.dao.DataIntegrityViolationException;
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
class AffiliationRequestTests {

	private static final String AFFILIATIONS = "/api/v1/affiliations";
	private static final String MINE = "/api/v1/affiliations/me";

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
	void catalogListsOnlyActiveUniversities() throws Exception {
		UUID active = createUniversity("Universidade Ativa", AccountStatus.ACTIVE);
		createUniversity("Universidade Pendente", AccountStatus.PENDING_APPROVAL);
		createUniversity("Universidade Suspensa", AccountStatus.SUSPENDED);
		createUniversity("Universidade Rejeitada", AccountStatus.REJECTED);

		as(createStudentWithProfile(), get("/api/v1/universities"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(active.toString()))
				.andExpect(jsonPath("$.content[0].name").value("Universidade Ativa"))
				.andExpect(jsonPath("$.content[0].acronym").value("UT"))
				.andExpect(jsonPath("$.content[*].name", not(hasItem("Universidade Suspensa"))))
				.andExpect(jsonPath("$.content[0].email").doesNotExist())
				.andExpect(jsonPath("$.content[0].approvalStatus").doesNotExist());
		mockMvc.perform(get("/api/v1/universities")).andExpect(status().isUnauthorized());
	}

	@Test
	void studentRequestsAffiliationWithActiveUniversity() throws Exception {
		User student = createStudentWithProfile();
		UUID university = createUniversity("UFAPE", AccountStatus.ACTIVE);

		request(student, university)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.university.id").value(university.toString()))
				.andExpect(jsonPath("$.university.name").value("UFAPE"))
				.andExpect(jsonPath("$.requestedAt").isNotEmpty());

		entityManager.flush();
		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM university_affiliations");
		assertThat(row.get("status")).isEqualTo("PENDING");
		assertThat(row.get("university_id")).isEqualTo(university);
		assertThat(row.get("student_id")).isEqualTo(studentIdOf(student));

		as(student, get(MINE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].status").value("PENDING"))
				.andExpect(jsonPath("$[0].university.name").value("UFAPE"));
	}

	@Test
	void requestRequiresStudentProfile() throws Exception {
		User student = createUser(UserRole.STUDENT);

		request(student, createUniversity("UFAPE", AccountStatus.ACTIVE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_FOUND"));
	}

	@Test
	void universityMustExistAndBeActive() throws Exception {
		User student = createStudentWithProfile();

		for (AccountStatus status : new AccountStatus[] { AccountStatus.PENDING_APPROVAL, AccountStatus.SUSPENDED,
				AccountStatus.REJECTED }) {
			request(student, createUniversity("Universidade " + status, status))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("UNIVERSITY_NOT_ACTIVE"));
		}
		request(student, UUID.randomUUID())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("UNIVERSITY_NOT_FOUND"));
		as(student, post(AFFILIATIONS).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		assertThat(affiliationRows()).isZero();
	}

	@Test
	void onlyOnePendingRequestPerStudent() throws Exception {
		User student = createStudentWithProfile();
		UUID first = createUniversity("Primeira", AccountStatus.ACTIVE);
		UUID second = createUniversity("Segunda", AccountStatus.ACTIVE);
		request(student, first).andExpect(status().isCreated());

		request(student, first).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AFFILIATION_ALREADY_PENDING"));
		request(student, second).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AFFILIATION_ALREADY_PENDING"));

		assertThat(affiliationRows()).isEqualTo(1);
	}

	@Test
	void studentWithApprovedAffiliationCannotRequestAnother() throws Exception {
		User student = createStudentWithProfile();
		UUID first = createUniversity("Primeira", AccountStatus.ACTIVE);
		request(student, first).andExpect(status().isCreated());
		setAffiliationStatus("APPROVED");

		request(student, createUniversity("Segunda", AccountStatus.ACTIVE))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AFFILIATION_ALREADY_APPROVED"));

		assertThat(affiliationRows()).isEqualTo(1);
	}

	@Test
	void newRequestIsAllowedAfterRejectionAndHistoryIsKept() throws Exception {
		User student = createStudentWithProfile();
		UUID university = createUniversity("UFAPE", AccountStatus.ACTIVE);
		request(student, university).andExpect(status().isCreated());
		setAffiliationStatus("REJECTED");

		request(student, university).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"));

		as(student, get(MINE))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].status").value("PENDING"))
				.andExpect(jsonPath("$[1].status").value("REJECTED"));
	}

	// Cada teste de restrição tem uma única inserção inválida: no PostgreSQL, a primeira falha aborta a transação.

	@Test
	void databaseRejectsSecondPendingRequestForTheSameStudent() throws Exception {
		assertThatThrownBy(() -> insertAfterSeededHistory("PENDING"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsSecondApprovedAffiliationForTheSameStudent() throws Exception {
		assertThatThrownBy(() -> insertAfterSeededHistory("APPROVED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsUnknownAffiliationStatus() throws Exception {
		assertThatThrownBy(() -> insertAfterSeededHistory("UNKNOWN"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	/** Histórico válido (1 pendente, 1 aprovado, 2 rejeitados) e, em seguida, a inserção sob teste. */
	private void insertAfterSeededHistory(String status) throws Exception {
		UUID studentId = studentIdOf(createStudentWithProfile());
		UUID first = createUniversity("Primeira", AccountStatus.ACTIVE);
		UUID second = createUniversity("Segunda", AccountStatus.ACTIVE);
		String insert = "INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, ?)";
		jdbcTemplate.update(insert, studentId, first, "PENDING");
		jdbcTemplate.update(insert, studentId, first, "APPROVED");
		jdbcTemplate.update(insert, studentId, first, "REJECTED");
		jdbcTemplate.update(insert, studentId, first, "REJECTED");

		jdbcTemplate.update(insert, studentId, second, status);
	}

	@Test
	void studentsOnlySeeTheirOwnRequestsAndOtherRolesAreBlocked() throws Exception {
		User ana = createStudentWithProfile();
		User bruno = createStudentWithProfile();
		UUID university = createUniversity("UFAPE", AccountStatus.ACTIVE);
		request(ana, university).andExpect(status().isCreated());

		as(bruno, get(MINE)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
		for (UserRole role : new UserRole[] { UserRole.COMPANY, UserRole.UNIVERSITY, UserRole.ADMIN }) {
			User user = createUser(role);
			request(user, university).andExpect(status().isForbidden());
			as(user, get(MINE)).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(MINE)).andExpect(status().isUnauthorized());

		assertThat(affiliationRows()).isEqualTo(1);
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
		entityManager.flush();
		return student;
	}

	private UUID studentIdOf(User student) {
		return jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class, student.getId());
	}

	private UUID createUniversity(String name, AccountStatus accountStatus) {
		User account = userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", UserRole.UNIVERSITY, accountStatus));
		return jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, acronym) VALUES (?, ?, 'UT') RETURNING id",
				UUID.class, account.getId(), name);
	}

	private void setAffiliationStatus(String status) {
		entityManager.flush();
		jdbcTemplate.update("UPDATE university_affiliations SET status = ?", status);
		entityManager.clear();
	}

	private int affiliationRows() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM university_affiliations", Integer.class);
	}

	private ResultActions request(User user, UUID universityId) throws Exception {
		return as(user, post(AFFILIATIONS).contentType(MediaType.APPLICATION_JSON)
				.content("{\"universityId\": \"%s\"}".formatted(universityId)));
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}
}
