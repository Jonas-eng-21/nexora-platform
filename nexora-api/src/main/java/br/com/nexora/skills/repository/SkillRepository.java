package br.com.nexora.skills.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nexora.skills.entity.Skill;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

	/** O nome deve estar normalizado ({@link Skill#normalize}). */
	Optional<Skill> findByNormalizedName(String normalizedName);

	/** O trecho deve estar normalizado ({@link Skill#normalize}); trecho vazio lista tudo. */
	Page<Skill> findByNormalizedNameContaining(String normalizedFragment, Pageable pageable);
}
