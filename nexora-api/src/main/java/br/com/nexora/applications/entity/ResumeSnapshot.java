package br.com.nexora.applications.entity;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.nexora.skills.entity.Skill;
import br.com.nexora.students.entity.Student;
import br.com.nexora.students.resume.Resume;

/**
 * Cópia imutável do que a empresa recebe numa candidatura PLATFORM: dados do perfil, currículo-base e as
 * competências do catálogo (que não ficam dentro do {@link Resume}). Gravada em JSONB e nunca atualizada.
 * <p>
 * De propósito, não traz e-mail nem id da conta. Mudanças de estrutura afetam snapshots já gravados: prefira
 * acrescentar campos opcionais a renomear ou remover.
 */
public record ResumeSnapshot(Profile profile, Resume resume, List<SkillItem> skills) {

	public record Profile(String fullName, String course, String institution, String location, String summary) {
	}

	public record SkillItem(UUID id, String name) {
	}

	public static ResumeSnapshot of(Student student) {
		return new ResumeSnapshot(
				new Profile(student.getFullName(), student.getCourse(), student.getInstitution(),
						student.getLocation(), student.getSummary()),
				student.getResume(),
				student.getSkills().stream()
						.sorted(Comparator.comparing(Skill::getNormalizedName))
						.map(skill -> new SkillItem(skill.getId(), skill.getName()))
						.toList());
	}
}
