package br.com.nexora.jobs.entity;

import java.util.Objects;

import br.com.nexora.skills.entity.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Competência exigida por uma vaga. É um valor da vaga (linha de {@code job_skills}), sem ciclo de vida próprio.
 * A igualdade considera só a competência, pois a mesma skill não pode ser obrigatória e desejável ao mesmo tempo.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobSkill {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "skill_id", nullable = false)
	private Skill skill;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SkillRequirement requirement;

	public JobSkill(Skill skill, SkillRequirement requirement) {
		this.skill = skill;
		this.requirement = requirement;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof JobSkill that && Objects.equals(skill.getId(), that.skill.getId());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(skill.getId());
	}
}

