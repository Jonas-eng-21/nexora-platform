package br.com.nexora.administration.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;

/**
 * Cria o administrador inicial na subida da aplicação, a partir de ADMIN_EMAIL e ADMIN_PASSWORD.
 * É o único caminho para criar um ADMIN: o cadastro público rejeita esse papel.
 * Idempotente: nunca altera uma conta já existente com o mesmo e-mail.
 */
@Component
public class AdminProvisioner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminProvisioner.class);
	private static final int MIN_PASSWORD_LENGTH = 12;
	private static final int BCRYPT_MAX_BYTES = 72;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String configuredEmail;
	private final String configuredPassword;

	public AdminProvisioner(UserRepository userRepository, PasswordEncoder passwordEncoder,
			@Value("${nexora.admin.email:}") String configuredEmail,
			@Value("${nexora.admin.password:}") String configuredPassword) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.configuredEmail = configuredEmail;
		this.configuredPassword = configuredPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		provision(configuredEmail, configuredPassword);
	}

	/** @return true somente quando uma nova conta ADMIN foi criada. */
	public boolean provision(String email, String rawPassword) {
		if (email == null || email.isBlank() || rawPassword == null || rawPassword.isBlank()) {
			log.info("Initial admin provisioning skipped: ADMIN_EMAIL/ADMIN_PASSWORD not set");
			return false;
		}
		Optional<User> existing = userRepository.findByEmail(User.normalizeEmail(email));
		if (existing.isPresent()) {
			if (existing.get().getRole() == UserRole.ADMIN) {
				log.info("Initial admin already exists; nothing to do");
			}
			else {
				log.warn("ADMIN_EMAIL belongs to an existing non-admin account; it was NOT promoted");
			}
			return false;
		}
		requireStrongPassword(rawPassword);
		userRepository.save(new User(email, passwordEncoder.encode(rawPassword), UserRole.ADMIN, AccountStatus.ACTIVE));
		log.info("Initial admin account created");
		return true;
	}

	// Falha a subida em vez de criar um admin com senha fraca. A mensagem nunca inclui a senha.
	private static void requireStrongPassword(String rawPassword) {
		int bytes = rawPassword.getBytes(StandardCharsets.UTF_8).length;
		if (rawPassword.length() < MIN_PASSWORD_LENGTH || bytes > BCRYPT_MAX_BYTES) {
			throw new IllegalStateException("ADMIN_PASSWORD must have at least " + MIN_PASSWORD_LENGTH
					+ " characters and at most " + BCRYPT_MAX_BYTES + " bytes");
		}
	}
}
