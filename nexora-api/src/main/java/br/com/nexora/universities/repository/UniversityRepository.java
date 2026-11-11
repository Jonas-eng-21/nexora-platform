package br.com.nexora.universities.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nexora.universities.entity.University;
import br.com.nexora.universities.entity.UniversityApprovalStatus;
import jakarta.persistence.LockModeType;

public interface UniversityRepository extends JpaRepository<University, UUID> {

	Optional<University> findByUserId(UUID userId);

	/** Bloqueia a linha até o fim da transação, para que duas decisões simultâneas não se sobreponham. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from University u where u.id = :id")
	Optional<University> findByIdForUpdate(@Param("id") UUID id);

	/** Filtro opcional: null significa "qualquer". */
	@Query("select u from University u where (:approvalStatus is null or u.approvalStatus = :approvalStatus)")
	Page<University> search(@Param("approvalStatus") UniversityApprovalStatus approvalStatus, Pageable pageable);
}
