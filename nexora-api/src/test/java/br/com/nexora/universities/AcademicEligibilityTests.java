package br.com.nexora.universities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.shared.security.TokenService;
import br.com.nexora.universities.service.AcademicEligibilityService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class AcademicEligibilityTests {

	private static final String ELIGIBILITY = "/api/v1/affiliations/me/eligibility";

	@Autowired
	private AcademicEligibilityService eligibilityService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;
	private User studentAccount;
	private UUID studentId;
	private User universityAccount;
	private UUID universityId;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		studentAccount = createUser(UserRole.STUDENT, AccountStatus.ACTIVE);
		studentId = createStudent(studentAccount);
		universityAccount = createUser(UserRole.UNIVERSITY, AccountStatus.ACTIVE);
		universityId = createUniversity(universityAccount);
	}

	@Test
	void studentWithoutAnyAffiliationIsNotEligible() {
		assertThat(eligibilityService.isEligible(studentId)).isFalse();
	}

	@Test
	void pendingRequestDoesNotGrantEligibility() {
		addAffiliation(studentId, universityId, "PENDING");

		assertThat(eligibilityService.isEligible(studentId)).isFalse();
	}

	@Test
	void rejectedRequestDoesNotGrantEligibility() {
		addAffiliation(studentId, universityId, "REJECTED");

		assertThat(eligibilityService.isEligible(studentId)).isFalse();
	}

	@Test
	void revokedAffiliationDoesNotGrantEligibility() {
		addAffiliation(studentId, universityId, "REVOKED");

		assertThat(eligibilityService.isEligible(studentId)).isFalse();
	}

	@Test
	void approvedAffiliationWithActiveUniversityGrantsEligibility() {
		addAffiliation(studentId, universityId, "REJECTED");
		addAffiliation(studentId, universityId, "REVOKED");
		addAffiliation(studentId, universityId, "APPROVED");

		assertThat(eligibilityService.isEligible(studentId)).isTrue();
		assertThatCode(() -> eligibilityService.requireEligible(studentId)).doesNotThrowAnyException();
	}

	@Test
	void suspendedUniversityRemovesEligibilityWhileSuspensionLasts() {
		addAffiliation(studentId, universityId, "APPROVED");

		setUniversityAccountStatus("SUSPENDED");
		assertThat(eligibilityService.isEligible(studentId)).isFalse();

		setUniversityAccountStatus("ACTIVE");
		assertThat(eligibilityService.isEligible(studentId)).isTrue();
	}

	@Test
	void universityThatIsNotActiveNeverGrantsEligibility() {
		addAffiliation(studentId, universityId, "APPROVED");

		for (String status : new String[] { "PENDING_APPROVAL", "REJECTED" }) {
			setUniversityAccountStatus(status);
			assertThat(eligibilityService.isEligible(studentId)).as(status).isFalse();
		}
	}

	@Test
	void eligibilityOfOneStudentDoesNotLeakToAnother() {
		UUID otherStudentId = createStudent(createUser(UserRole.STUDENT, AccountStatus.ACTIVE));
		addAffiliation(otherStudentId, universityId, "APPROVED");

		assertThat(eligibilityService.isEligible(otherStudentId)).isTrue();
		assertThat(eligibilityService.isEligible(studentId)).isFalse();
	}

	@Test
	void requireEligibleRejectsWithStableBusinessCode() {
		addAffiliation(studentId, universityId, "REVOKED");

		assertThatThrownBy(() -> eligibilityService.requireEligible(studentId))
				.isInstanceOfSatisfying(ApiException.class, ex -> {
					assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
					assertThat(ex.getCode()).isEqualTo("STUDENT_NOT_VALIDATED");
				});
	}

	@Test
	void studentSeesOwnEligibility() throws Exception {
		String bearer = "Bearer " + tokenService.issue(studentAccount);
		mockMvc.perform(get(ELIGIBILITY).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eligible").value(false));

		addAffiliation(studentId, universityId, "APPROVED");

		mockMvc.perform(get(ELIGIBILITY).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eligible").value(true));
		mockMvc.perform(get(ELIGIBILITY)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(universityAccount)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(ELIGIBILITY)).andExpect(status().isUnauthorized());
	}

	private User createUser(UserRole role, AccountStatus status) {
		return userRepository.saveAndFlush(new User(UUID.randomUUID() + "@nexora.test", "hash", role, status));
	}

	private UUID createStudent(User account) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO students (user_id, full_name) VALUES (?, 'Estudante Teste') RETURNING id",
				UUID.class, account.getId());
	}

	private UUID createUniversity(User account) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'UFAPE', 'APPROVED') RETURNING id",
				UUID.class, account.getId());
	}

	private void addAffiliation(UUID student, UUID university, String status) {
		jdbcTemplate.update("INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, ?)",
				student, university, status);
	}

	private void setUniversityAccountStatus(String status) {
		jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status, universityAccount.getId());
		entityManager.clear();
	}
}
