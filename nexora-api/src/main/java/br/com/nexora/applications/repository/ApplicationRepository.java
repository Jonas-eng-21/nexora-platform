package br.com.nexora.applications.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.entity.SuspensionReason;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

	boolean existsByStudentIdAndJobId(UUID studentId, UUID jobId);

	/**
	 * Suspende de uma vez as candidaturas em andamento dos estudantes informados. No SQL, o lado direito de um
	 * SET enxerga os valores antigos da linha: {@code suspended_from_status = status} guarda o estado anterior. O
	 * WHERE só alcança SUBMITTED e IN_REVIEW, então candidaturas finais e já suspensas ficam intactas (e um
	 * UPDATE concorrente que as tenha mudado é reavaliado após a espera pela trava da linha). O lote não passa
	 * pelo @UpdateTimestamp, então o updatedAt é informado aqui.
	 */
	@Modifying(flushAutomatically = true)
	@Query("""
			update Application a set a.suspendedFromStatus = a.status,
			    a.status = br.com.nexora.applications.entity.ApplicationStatus.SUSPENDED,
			    a.suspensionReason = :reason, a.suspendedAt = :now, a.updatedAt = :now
			where a.student.id in :studentIds
			  and a.status in (br.com.nexora.applications.entity.ApplicationStatus.SUBMITTED,
			                   br.com.nexora.applications.entity.ApplicationStatus.IN_REVIEW)
			""")
	int suspendInProgress(@Param("studentIds") Collection<UUID> studentIds,
			@Param("reason") SuspensionReason reason, @Param("now") Instant now);

	@Query("""
			select new br.com.nexora.applications.repository.ApplicationParties(
			    a.student.id, a.student.userId, a.job.company.userId, a.job.id)
			from Application a where a.id = :id
			""")
	Optional<ApplicationParties> findParties(@Param("id") UUID id);

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
