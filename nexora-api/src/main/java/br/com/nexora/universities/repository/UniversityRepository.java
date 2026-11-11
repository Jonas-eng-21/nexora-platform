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

	/** Universidades cuja conta está ACTIVE: as únicas que podem receber pedidos e validar vínculos. */
	@Query("""
			select u from University u
			where exists (select 1 from User a where a.id = u.userId
			              and a.status = br.com.nexora.users.entity.AccountStatus.ACTIVE)
			""")
	Page<University> findActive(Pageable pageable);

	/** Filtro opcional: null significa "qualquer". */
	@Query("select u from University u where (:approvalStatus is null or u.approvalStatus = :approvalStatus)")
	Page<University> search(@Param("approvalStatus") UniversityApprovalStatus approvalStatus, Pageable pageable);
}
