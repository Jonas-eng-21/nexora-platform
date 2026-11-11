package br.com.nexora.skills.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.skills.entity.Skill;
import br.com.nexora.skills.repository.SkillRepository;

@Service
public class SkillService {

	private final SkillRepository skillRepository;

	public SkillService(SkillRepository skillRepository) {
		this.skillRepository = skillRepository;
	}

	public record AddResult(Skill skill, boolean created) {
	}

	@Transactional(readOnly = true)
	public Page<Skill> search(String search, Pageable pageable) {
		return skillRepository.findByNormalizedNameContaining(Skill.normalize(search == null ? "" : search), pageable);
	}

	/** Devolve a competência existente quando o nome é uma duplicata lógica; caso contrário, cria. */
	@Transactional
	public AddResult add(String name) {
		Optional<Skill> existing = skillRepository.findByNormalizedName(Skill.normalize(name));
		if (existing.isPresent()) {
			return new AddResult(existing.get(), false);
		}
		return new AddResult(skillRepository.save(new Skill(name)), true);
	}

	/** Usado por estudantes e vagas ao associar competências: todas devem existir no catálogo. */
	@Transactional(readOnly = true)
	public List<Skill> getAllByIds(Set<UUID> skillIds) {
		List<Skill> skills = skillRepository.findAllById(skillIds);
		if (skills.size() != skillIds.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SKILL_NOT_FOUND",
					"Uma ou mais competências não existem no catálogo.");
		}
		return skills;
	}
}
