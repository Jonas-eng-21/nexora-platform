package br.com.nexora.matching.calculator;

import java.util.Set;
import java.util.UUID;

import br.com.nexora.shared.domain.ProfessionalArea;
import br.com.nexora.shared.domain.WorkModel;

/**
 * Dados que alimentam o cálculo. Do lado do estudante vêm apenas do currículo-base estruturado e das
 * competências do catálogo: um PDF anexado a uma candidatura nunca entra aqui.
 */
public final class MatchInput {

	private MatchInput() {
	}

	public record SkillRef(UUID id, String name) {
	}

	public record Candidate(Set<SkillRef> skills, Set<ProfessionalArea> interestAreas,
			Set<WorkModel> preferredWorkModels, String location) {

		public Candidate {
			skills = Set.copyOf(skills);
			interestAreas = Set.copyOf(interestAreas);
			preferredWorkModels = Set.copyOf(preferredWorkModels);
		}
	}

	public record Position(Set<SkillRef> requiredSkills, Set<SkillRef> desiredSkills, ProfessionalArea area,
			WorkModel workModel, String location) {

		public Position {
			requiredSkills = Set.copyOf(requiredSkills);
			desiredSkills = Set.copyOf(desiredSkills);
		}
	}
}
