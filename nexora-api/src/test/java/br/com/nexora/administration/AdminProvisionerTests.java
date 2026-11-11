package br.com.nexora.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.nexora.administration.service.AdminProvisioner;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;

@SpringBootTest
@Transactional
class AdminProvisionerTests {

	private static final String EMAIL = "root@nexora.test";
	private static final String PASSWORD = "Adm1n-s3nha-forte!";

	@Autowired
	private AdminProvisioner adminProvisioner;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private WebApplicationContext context;

	@Test
	void createsActiveAdminWithHashedPassword() {
		assertThat(adminProvisioner.provision("Root@Nexora.test", PASSWORD)).isTrue();

		User admin = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(admin.getPasswordHash()).isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
	}

	@Test
	void provisionedAdminCanLogIn() throws Exception {
		adminProvisioner.provision(EMAIL, PASSWORD);

		MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
				.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void isIdempotentAndNeverOverwritesExistingAdmin() {
		adminProvisioner.provision(EMAIL, PASSWORD);
		String originalHash = userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash();
		long usersBefore = userRepository.count();

		assertThat(adminProvisioner.provision(EMAIL, "Outra-s3nha-diferente!")).isFalse();

		assertThat(userRepository.count()).isEqualTo(usersBefore);
		assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash()).isEqualTo(originalHash);
	}

	@Test
	void doesNothingWhenCredentialsAreNotConfigured() {
		long usersBefore = userRepository.count();

		assertThat(adminProvisioner.provision("", "")).isFalse();
		assertThat(adminProvisioner.provision(EMAIL, " ")).isFalse();
		assertThat(adminProvisioner.provision(null, null)).isFalse();

		assertThat(userRepository.count()).isEqualTo(usersBefore);
	}

	@Test
	void neverPromotesAnExistingNonAdminAccount() {
		userRepository.saveAndFlush(new User(EMAIL, "hash", UserRole.STUDENT, AccountStatus.ACTIVE));

		assertThat(adminProvisioner.provision(EMAIL, PASSWORD)).isFalse();

		User existing = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(existing.getRole()).isEqualTo(UserRole.STUDENT);
		assertThat(existing.getPasswordHash()).isEqualTo("hash");
	}

	@Test
	void rejectsWeakPasswordWithoutCreatingAdmin() {
		assertThatThrownBy(() -> adminProvisioner.provision(EMAIL, "curta"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("curta");

		assertThat(userRepository.findByEmail(EMAIL)).isEmpty();
	}
}
