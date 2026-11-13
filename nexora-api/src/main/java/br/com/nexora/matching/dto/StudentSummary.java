package br.com.nexora.matching.dto;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.nexora.shared.domain.ProfessionalArea;
import br.com.nexora.skills.entity.Skill;
import br.com.nexora.students.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * O que a empresa pode ver de um estudante recomendado. De propósito, não traz e-mail, conta, instituição de
 * texto livre, links nem o currículo: o currículo completo só chega à empresa por meio de uma candidatura.
 */
@Schema(description = "Dados do estudante autorizados para a empresa: sem e-mail e sem o currículo completo")
public record StudentSummary(
		@Schema(description = "Id do perfil de estudante (não o da conta)") UUID id,
		String fullName,
		String course,
		String location,
		String summary,
		@Schema(description = "Nomes das competências do catálogo, em ordem alfabética") List<String> skills,
		List<ProfessionalArea> interestAreas) {

	public static StudentSummary from(Student student) {
		return new StudentSummary(student.getId(), student.getFullName(), student.getCourse(),
				student.getLocation(), student.getSummary(),
				student.getSkills().stream()
						.sorted(Comparator.comparing(Skill::getNormalizedName))
						.map(Skill::getName)
						.toList(),
				student.getResume().interestAreas());
	}
}
