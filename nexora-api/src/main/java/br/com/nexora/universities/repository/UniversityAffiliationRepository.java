package br.com.nexora.universities.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nexora.universities.entity.AffiliationStatus;
import br.com.nexora.universities.entity.UniversityAffiliation;

public interface UniversityAffiliationRepository extends JpaRepository<UniversityAffiliation, UUID> {

	boolean existsByStudentIdAndStatus(UUID studentId, AffiliationStatus status);

	@EntityGraph(attributePaths = "university")
	List<UniversityAffiliation> findByStudentIdOrderByRequestedAtDesc(UUID studentId);
}
