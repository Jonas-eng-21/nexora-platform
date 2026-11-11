package br.com.nexora.shared.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class SecurityTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JwtEncoder jwtEncoder;

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
	void activeUserWithValidTokenIsAuthenticatedWithIdentityFromDatabase() throws Exception {
		User student = createUser(UserRole.STUDENT, AccountStatus.ACTIVE);

		getWithToken("/test-security/me", tokenService.issue(student))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(student.getId().toString()))
				.andExpect(jsonPath("$.role").value("STUDENT"));
	}

	@Test
	void requestWithoutTokenReturns401() throws Exception {
		mockMvc.perform(get("/test-security/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void malformedTokenReturns401() throws Exception {
		expectUnauthenticated(getWithToken("/test-security/me", "not-a-jwt"));
	}

	@Test
	void tokenSignedWithAnotherSecretReturns401() throws Exception {
		User student = createUser(UserRole.STUDENT, AccountStatus.ACTIVE);
		JwtEncoder foreignEncoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(
				"another-secret-with-at-least-32-bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();

		expectUnauthenticated(getWithToken("/test-security/me",
				encode(foreignEncoder, student.getId().toString(), Instant.now().plus(1, ChronoUnit.HOURS))));
	}

	@Test
	void expiredTokenReturns401() throws Exception {
		User student = createUser(UserRole.STUDENT, AccountStatus.ACTIVE);

		expectUnauthenticated(getWithToken("/test-security/me",
				encode(jwtEncoder, student.getId().toString(), Instant.now().minus(1, ChronoUnit.HOURS))));
	}

	@Test
	void tokenOfUnknownUserReturns401() throws Exception {
		expectUnauthenticated(getWithToken("/test-security/me",
				encode(jwtEncoder, UUID.randomUUID().toString(), Instant.now().plus(1, ChronoUnit.HOURS))));
	}

	@Test
	void userSuspendedAfterTokenWasIssuedIsBlocked() throws Exception {
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);
		String token = tokenService.issue(company);
		getWithToken("/test-security/me", token).andExpect(status().isOk());

		jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", company.getId());
		entityManager.clear();

		getWithToken("/test-security/me", token)
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
	}

	@Test
	void universityPendingApprovalIsBlocked() throws Exception {
		User university = createUser(UserRole.UNIVERSITY, AccountStatus.PENDING_APPROVAL);

		getWithToken("/test-security/me", tokenService.issue(university))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_APPROVAL"));
	}

	@Test
	void adminRoutesRejectOtherRoles() throws Exception {
		for (UserRole role : new UserRole[] { UserRole.STUDENT, UserRole.COMPANY, UserRole.UNIVERSITY }) {
			User user = createUser(role, AccountStatus.ACTIVE);

			getWithToken("/api/v1/admin/test-ping", tokenService.issue(user))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		}
	}

	@Test
	void adminRoutesAcceptAdmin() throws Exception {
		User admin = createUser(UserRole.ADMIN, AccountStatus.ACTIVE);

		getWithToken("/api/v1/admin/test-ping", tokenService.issue(admin)).andExpect(status().isOk());
	}

	@Test
	void methodLevelRoleRestrictionIsEnforced() throws Exception {
		User student = createUser(UserRole.STUDENT, AccountStatus.ACTIVE);
		User company = createUser(UserRole.COMPANY, AccountStatus.ACTIVE);

		getWithToken("/test-security/company-only", tokenService.issue(student))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		getWithToken("/test-security/company-only", tokenService.issue(company)).andExpect(status().isOk());
	}

	private User createUser(UserRole role, AccountStatus status) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, status));
	}

	private ResultActions getWithToken(String path, String token) throws Exception {
		return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private static void expectUnauthenticated(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	private static String encode(JwtEncoder encoder, String subject, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(subject)
				.issuedAt(expiresAt.minus(2, ChronoUnit.HOURS))
				.expiresAt(expiresAt)
				.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}
}
