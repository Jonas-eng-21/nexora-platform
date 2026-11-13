package br.com.nexora.applications.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.entity.ApplicationStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

	boolean existsByStudentIdAndJobId(UUID studentId, UUID jobId);

	/** Bloqueia a linha até o fim da transação, para duas decisões simultâneas não se sobreporem. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Application a where a.id = :id")
	Optional<Application> findByIdForUpdate(@Param("id") UUID id);

	/**
	 * Candidaturas do estudante dono da conta informada (não do perfil, para o cliente nunca indicar de quem
	 * são). Vaga e empresa vêm no mesmo SELECT, evitando uma consulta por linha. Status null significa "qualquer".
	 */
	@Query(value = """
			select a from Application a join fetch a.job j join fetch j.company
			where a.student.userId = :userId and (:status is null or a.status = :status)
			""", countQuery = """
			select count(a) from Application a
			where a.student.userId = :userId and (:status is null or a.status = :status)
			""")
	Page<Application> searchByStudentUser(@Param("userId") UUID userId, @Param("status") ApplicationStatus status,
			Pageable pageable);

	/**
	 * Candidaturas de uma vaga. O estudante vem no mesmo SELECT (e as competências dele em lote, por
	 * {@code default_batch_fetch_size}), evitando uma consulta por linha. Status null significa "qualquer".
	 */
	@Query(value = """
			select a from Application a join fetch a.student
			where a.job.id = :jobId and (:status is null or a.status = :status)
			""", countQuery = """
			select count(a) from Application a
			where a.job.id = :jobId and (:status is null or a.status = :status)
			""")
	Page<Application> searchByJob(@Param("jobId") UUID jobId, @Param("status") ApplicationStatus status,
			Pageable pageable);
}
