package br.com.nexora.skills.dto;

import java.util.UUID;

import br.com.nexora.skills.entity.Skill;

public record SkillResponse(UUID id, String name) {

	public static SkillResponse from(Skill skill) {
		return new SkillResponse(skill.getId(), skill.getName());
	}
}
