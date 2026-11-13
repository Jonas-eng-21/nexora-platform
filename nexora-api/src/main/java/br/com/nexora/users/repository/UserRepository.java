package br.com.nexora.users.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {

	/**
	 * Lê a conta ativa com trava compartilhada (FOR SHARE) até o fim da transação. Conflita com a atualização que
	 * suspende a conta, então uma operação que depende de a conta estar ativa e a suspensão se enfileiram.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select u from User u where u.id = :id and u.status = br.com.nexora.users.entity.AccountStatus.ACTIVE")
	Optional<User> findActiveByIdForShare(@Param("id") UUID id);

	/** O e-mail deve estar normalizado ({@link User#normalizeEmail}). */
	boolean existsByEmail(String email);

	/** O e-mail deve estar normalizado ({@link User#normalizeEmail}). */
	Optional<User> findByEmail(String email);

	/** Filtros opcionais: null significa "qualquer". */
	@Query("""
			select u from User u
			where (:role is null or u.role = :role)
			  and (:status is null or u.status = :status)
			""")
	Page<User> search(@Param("role") UserRole role, @Param("status") AccountStatus status, Pageable pageable);
}
