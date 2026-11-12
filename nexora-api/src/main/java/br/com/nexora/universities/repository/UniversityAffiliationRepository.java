package br.com.nexora.universities.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nexora.universities.entity.AffiliationStatus;
import br.com.nexora.universities.entity.UniversityAffiliation;
import jakarta.persistence.LockModeType;

public interface UniversityAffiliationRepository extends JpaRepository<UniversityAffiliation, UUID> {

	boolean existsByStudentIdAndStatus(UUID studentId, AffiliationStatus status);

	@EntityGraph(attributePaths = "university")
	List<UniversityAffiliation> findByStudentIdOrderByRequestedAtDesc(UUID studentId);

	/** Regra de elegibilidade acadêmica: vínculo APPROVED com universidade de conta ACTIVE. */
	@Query("""
			select count(a) > 0 from UniversityAffiliation a
			where a.student.id = :studentId
			  and a.status = br.com.nexora.universities.entity.AffiliationStatus.APPROVED
			  and exists (select 1 from User u where u.id = a.university.userId
			              and u.status = br.com.nexora.users.entity.AccountStatus.ACTIVE)
			""")
	boolean existsApprovedWithActiveUniversity(@Param("studentId") UUID studentId);

	/** Bloqueia a linha até o fim da transação, para que duas decisões simultâneas não se sobreponham. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from UniversityAffiliation a where a.id = :id")
	Optional<UniversityAffiliation> findByIdForUpdate(@Param("id") UUID id);

	/** Pedidos endereçados a uma universidade. Status null significa "qualquer". */
	@EntityGraph(attributePaths = { "student", "university" })
	@Query("""
			select a from UniversityAffiliation a
			where a.university.id = :universityId and (:status is null or a.status = :status)
			""")
	Page<UniversityAffiliation> searchByUniversity(@Param("universityId") UUID universityId,
			@Param("status") AffiliationStatus status, Pageable pageable);

	/** Todos os pedidos (visão do administrador). Status null significa "qualquer". */
	@EntityGraph(attributePaths = { "student", "university" })
	@Query("select a from UniversityAffiliation a where (:status is null or a.status = :status)")
	Page<UniversityAffiliation> searchAll(@Param("status") AffiliationStatus status, Pageable pageable);
}
