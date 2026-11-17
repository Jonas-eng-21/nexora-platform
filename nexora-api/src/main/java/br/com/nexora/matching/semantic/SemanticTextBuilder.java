package br.com.nexora.matching.semantic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import br.com.nexora.shared.domain.ProfessionalArea;
import br.com.nexora.shared.domain.WorkModel;
import br.com.nexora.students.resume.Resume;

/**
 * Representação textual determinística do currículo-base e da vaga, enviada ao provedor de embeddings.
 * <p>
 * Só usa campos do currículo-base e da vaga relevantes para a comparação. Nunca inclui e-mail, links, nomes de
 * pessoas ou de organizações, instituição de ensino, localização nem conteúdo de PDF ou de snapshot de candidatura.
 * Listas saem em ordem alfabética e espaços são normalizados, para o mesmo conteúdo gerar sempre o mesmo texto
 * (base da chave de reaproveitamento dos embeddings). Textos acima de {@code maxLength} são cortados.
 */
public final class SemanticTextBuilder {

	private SemanticTextBuilder() {
	}

	public static String forStudent(String summary, String course, Collection<String> skills, Resume resume,
			int maxLength) {
		List<String> lines = new ArrayList<>();
		lines.add("[Estudante]");
		add(lines, "Resumo", clean(summary));
		add(lines, "Curso", clean(course));
		add(lines, "Competências", sorted(skills));
		add(lines, "Experiência", resume.experiences().stream()
				.map(e -> join(clean(e.role()), clean(e.description()))).collect(Collectors.joining("; ")));
		add(lines, "Projetos", resume.projects().stream()
				.map(p -> join(clean(p.name()), clean(p.description()))).collect(Collectors.joining("; ")));
		add(lines, "Cursos", resume.courses().stream().map(c -> clean(c.name()))
				.collect(Collectors.joining("; ")));
		add(lines, "Interesses", sorted(resume.interests()));
		add(lines, "Áreas de interesse", resume.interestAreas().stream().map(ProfessionalArea::name).sorted()
				.collect(Collectors.joining(", ")));
		return truncate(String.join("\n", lines), maxLength);
	}

	public static String forJob(String title, String description, ProfessionalArea area, WorkModel workModel,
			Collection<String> requiredSkills, Collection<String> desiredSkills, int maxLength) {
		List<String> lines = new ArrayList<>();
		lines.add("[Vaga]");
		add(lines, "Título", clean(title));
		add(lines, "Descrição", clean(description));
		add(lines, "Área", area == null ? "" : area.name());
		add(lines, "Competências obrigatórias", sorted(requiredSkills));
		add(lines, "Competências desejáveis", sorted(desiredSkills));
		add(lines, "Modalidade", workModel == null ? "" : workModel.name());
		return truncate(String.join("\n", lines), maxLength);
	}

	private static void add(List<String> lines, String label, String value) {
		if (value != null && !value.isBlank()) {
			lines.add(label + ": " + value);
		}
	}

	private static String join(String first, String second) {
		return second.isBlank() ? first : first + " - " + second;
	}

	private static String sorted(Collection<String> values) {
		return values.stream().filter(Objects::nonNull).map(SemanticTextBuilder::clean).filter(v -> !v.isBlank())
				.collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))).stream()
				.collect(Collectors.joining(", "));
	}

	private static String clean(String value) {
		return value == null ? "" : value.strip().replaceAll("\\s+", " ");
	}

	private static String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength).stripTrailing();
	}
}
