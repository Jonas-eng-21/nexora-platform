package br.com.nexora.users.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nexora.users.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

	/** O e-mail deve estar normalizado ({@link User#normalizeEmail}). */
	boolean existsByEmail(String email);

	/** O e-mail deve estar normalizado ({@link User#normalizeEmail}). */
	Optional<User> findByEmail(String email);
}
