package br.com.nexora.universities.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nexora.universities.entity.University;

public interface UniversityRepository extends JpaRepository<University, UUID> {

	Optional<University> findByUserId(UUID userId);
}
