package br.com.nexora.jobs.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nexora.jobs.entity.Job;
import br.com.nexora.jobs.entity.JobLevel;
import br.com.nexora.jobs.entity.JobStatus;
import br.com.nexora.shared.domain.ProfessionalArea;
import br.com.nexora.shared.domain.WorkModel;

public interface JobRepository extends JpaRepository<Job, UUID> {

	Optional<Job> findByIdAndDeletedAtIsNull(UUID id);

	Optional<Job> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);

	@Query("""
			SELECT j FROM Job j
			WHERE j.id = :id
			  AND j.deletedAt IS NULL
			  AND j.status = br.com.nexora.jobs.entity.JobStatus.OPEN
			  AND EXISTS (
			      SELECT 1 FROM User u
			      WHERE u.id = j.company.userId
			        AND u.status = br.com.nexora.users.entity.AccountStatus.ACTIVE
			  )
			""")
	Optional<Job> findOpenFromActiveCompanyById(@Param("id") UUID id);

	@Query("""
			SELECT j FROM Job j
			WHERE j.company.id = :companyId
			  AND j.deletedAt IS NULL
			  AND (:status IS NULL OR j.status = :status)
			""")
	Page<Job> findActiveByCompanyId(@Param("companyId") UUID companyId, @Param("status") JobStatus status,
			Pageable pageable);

	@Query("""
			SELECT j FROM Job j
			WHERE j.deletedAt IS NULL
			  AND j.status = br.com.nexora.jobs.entity.JobStatus.OPEN
			  AND EXISTS (
			      SELECT 1 FROM User u
			      WHERE u.id = j.company.userId
			        AND u.status = br.com.nexora.users.entity.AccountStatus.ACTIVE
			  )
			  AND (:search = '' OR LOWER(j.title) LIKE CONCAT('%', :search, '%')
			       OR LOWER(j.description) LIKE CONCAT('%', :search, '%')
			       OR LOWER(j.company.name) LIKE CONCAT('%', :search, '%'))
			  AND (:area IS NULL OR j.area = :area)
			  AND (:location = '' OR LOWER(COALESCE(j.location, '')) LIKE CONCAT('%', :location, '%'))
			  AND (:workModel IS NULL OR j.workModel = :workModel)
			  AND (:level IS NULL OR j.level = :level)
			""")
	Page<Job> searchOpen(@Param("search") String search, @Param("area") ProfessionalArea area,
			@Param("location") String location, @Param("workModel") WorkModel workModel,
			@Param("level") JobLevel level, Pageable pageable);

	@Query("""
			SELECT j FROM Job j
			WHERE j.deletedAt IS NULL
			  AND j.status = br.com.nexora.jobs.entity.JobStatus.OPEN
			  AND EXISTS (
			      SELECT 1 FROM User u
			      WHERE u.id = j.company.userId
			        AND u.status = br.com.nexora.users.entity.AccountStatus.ACTIVE
			  )
			  AND (:search = '' OR LOWER(j.title) LIKE CONCAT('%', :search, '%')
			       OR LOWER(j.description) LIKE CONCAT('%', :search, '%')
			       OR LOWER(j.company.name) LIKE CONCAT('%', :search, '%'))
			  AND (:area IS NULL OR j.area = :area)
			  AND (:location = '' OR LOWER(COALESCE(j.location, '')) LIKE CONCAT('%', :location, '%'))
			  AND (:workModel IS NULL OR j.workModel = :workModel)
			  AND (:level IS NULL OR j.level = :level)
			  AND EXISTS (SELECT 1 FROM j.skills js WHERE js.skill.id = :skillId)
			""")
	Page<Job> searchOpenBySkill(@Param("search") String search, @Param("area") ProfessionalArea area,
			@Param("location") String location, @Param("workModel") WorkModel workModel,
			@Param("level") JobLevel level, @Param("skillId") UUID skillId, Pageable pageable);
}
