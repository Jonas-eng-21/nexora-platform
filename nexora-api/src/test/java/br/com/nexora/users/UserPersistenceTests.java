package br.com.nexora.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class UserPersistenceTests {

	private static final String INSERT_SQL = """
			INSERT INTO users (email, password_hash, role, status)
			VALUES (?, 'hash', ?, ?)
			""";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void persistsUserWithGeneratedIdAndTimestamps() {
		User saved = userRepository.saveAndFlush(
				new User("student@nexora.test", "hash", UserRole.STUDENT, AccountStatus.ACTIVE));
		entityManager.clear();

		User found = userRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getEmail()).isEqualTo("student@nexora.test");
		assertThat(found.getPasswordHash()).isEqualTo("hash");
		assertThat(found.getRole()).isEqualTo(UserRole.STUDENT);
		assertThat(found.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
	}

	@Test
	void rejectsDuplicateEmail() {
		userRepository.saveAndFlush(
				new User("dup@nexora.test", "hash", UserRole.STUDENT, AccountStatus.ACTIVE));

		assertThatThrownBy(() -> userRepository.saveAndFlush(
				new User("dup@nexora.test", "hash", UserRole.COMPANY, AccountStatus.ACTIVE)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void treatsEmailAsCaseInsensitiveForUniqueness() {
		userRepository.saveAndFlush(
				new User("case@nexora.test", "hash", UserRole.STUDENT, AccountStatus.ACTIVE));

		assertThatThrownBy(() -> userRepository.saveAndFlush(
				new User("  Case@Nexora.TEST ", "hash", UserRole.STUDENT, AccountStatus.ACTIVE)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseGeneratesIdAndTimestampsForDirectInserts() {
		jdbcTemplate.update(INSERT_SQL, "direct@nexora.test", "COMPANY", "ACTIVE");

		Integer complete = jdbcTemplate.queryForObject("""
				SELECT count(*) FROM users
				WHERE email = 'direct@nexora.test'
				  AND id IS NOT NULL AND created_at IS NOT NULL AND updated_at IS NOT NULL
				""", Integer.class);

		assertThat(complete).isEqualTo(1);
	}

	@Test
	void databaseRejectsNonLowercaseEmail() {
		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SQL, "Upper@nexora.test", "STUDENT", "ACTIVE"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsUnknownRole() {
		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SQL, "role@nexora.test", "SUPERUSER", "ACTIVE"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsUnknownStatus() {
		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SQL, "status@nexora.test", "STUDENT", "DELETED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
