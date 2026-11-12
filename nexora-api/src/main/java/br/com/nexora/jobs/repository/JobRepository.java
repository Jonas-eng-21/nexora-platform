package br.com.nexora.jobs.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nexora.jobs.entity.Job;
import br.com.nexora.jobs.entity.JobStatus;

public interface JobRepository extends JpaRepository<Job, UUID> {

	Optional<Job> findByIdAndDeletedAtIsNull(UUID id);

	Optional<Job> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);

	@Query("""
			SELECT j FROM Job j
			WHERE j.company.id = :companyId
			  AND j.deletedAt IS NULL
			  AND (:status IS NULL OR j.status = :status)
			""")
	Page<Job> findActiveByCompanyId(@Param("companyId") UUID companyId, @Param("status") JobStatus status,
			Pageable pageable);
}
