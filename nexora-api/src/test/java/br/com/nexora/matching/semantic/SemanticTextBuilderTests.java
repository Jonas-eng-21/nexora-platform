package br.com.nexora.matching.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.nexora.shared.domain.ProfessionalArea;
import br.com.nexora.shared.domain.WorkModel;
import br.com.nexora.students.resume.Resume;

class SemanticTextBuilderTests {

	private static final Resume RESUME = new Resume(
			List.of(new Resume.Education("UPE Garanhuns", "Sistemas de Informação", "Bacharelado", 2023, null)),
			List.of(new Resume.Experience("Acme", "Estagiário", "Manutenção de APIs REST", "2025-01", null)),
			List.of(new Resume.Project("API de finanças", "Controle de gastos com Spring Boot",
					"https://github.com/fulano/financas")),
			List.of(new Resume.Course("Java Avançado", "Alura", 2025, "https://cert.example/abc")),
			List.of("Comunicação"), List.of("Software livre"),
			new Resume.Links("https://github.com/fulano", "https://linkedin.com/in/fulano", "https://fulano.dev"),
			List.of(ProfessionalArea.BACKEND), List.of(WorkModel.REMOTE));

	@Test
	void studentTextUsesOnlyResumeAndCatalogContent() {
		String text = SemanticTextBuilder.forStudent("Desenvolvedor focado em APIs", "Sistemas de Informação",
				List.of("Spring Boot", "Java"), RESUME, 4000);

		assertThat(text).startsWith("[Estudante]");
		assertThat(text).contains("Resumo: Desenvolvedor focado em APIs");
		assertThat(text).contains("Competências: Java, Spring Boot");
		assertThat(text).contains("API de finanças").contains("Controle de gastos com Spring Boot");
		assertThat(text).contains("Estagiário").contains("Manutenção de APIs REST");
		assertThat(text).contains("Áreas de interesse: BACKEND");
	}

	@Test
	void studentTextNeverCarriesLinksContactOrOrganisationNames() {
		String text = SemanticTextBuilder.forStudent("resumo", "curso", List.of("Java"), RESUME, 4000);

		assertThat(text).doesNotContain("http").doesNotContain("github").doesNotContain("linkedin")
				.doesNotContain("@").doesNotContain("fulano");
	}

	@Test
	void studentTextIsStableRegardlessOfListOrder() {
		String first = SemanticTextBuilder.forStudent("r", "c", List.of("Java", "Docker", "Spring Boot"), RESUME, 4000);
		String second = SemanticTextBuilder.forStudent("r", "c", List.of("Spring Boot", "Docker", "Java"), RESUME, 4000);

		assertThat(first).isEqualTo(second);
	}

	@Test
	void emptyStudentFieldsAreSkippedInsteadOfEmittedAsBlankLines() {
		String text = SemanticTextBuilder.forStudent(null, null, List.of(), Resume.empty(), 4000);

		assertThat(text).isEqualTo("[Estudante]");
	}

	@Test
	void jobTextHasTitleDescriptionSkillsAndModel() {
		String text = SemanticTextBuilder.forJob("Desenvolvedor Backend Java", "Serviços web e APIs",
				ProfessionalArea.BACKEND, WorkModel.REMOTE, Set.of("Spring Boot", "Java"), Set.of("PostgreSQL"), 4000);

		assertThat(text).isEqualTo("""
				[Vaga]
				Título: Desenvolvedor Backend Java
				Descrição: Serviços web e APIs
				Área: BACKEND
				Competências obrigatórias: Java, Spring Boot
				Competências desejáveis: PostgreSQL
				Modalidade: REMOTE""");
	}

	@Test
	void whitespaceIsNormalised() {
		String text = SemanticTextBuilder.forJob("  Vaga \n  Java ", "linha 1\n\n   linha 2", ProfessionalArea.OTHER,
				WorkModel.HYBRID, Set.of(), Set.of(), 4000);

		assertThat(text).contains("Título: Vaga Java").contains("Descrição: linha 1 linha 2");
	}

	@Test
	void textIsTruncatedToTheConfiguredLimit() {
		String text = SemanticTextBuilder.forJob("Vaga", "x".repeat(10_000), ProfessionalArea.OTHER, WorkModel.HYBRID,
				Set.of(), Set.of(), 500);

		assertThat(text).hasSize(500);
		assertThat(text).startsWith("[Vaga]");
	}

	@Test
	void sameInputGivesTheSameText() {
		Set<String> skills = Set.of("Java", "Docker");
		assertThat(SemanticTextBuilder.forJob("t", "d", ProfessionalArea.DATA, WorkModel.ON_SITE, skills, skills, 4000))
				.isEqualTo(SemanticTextBuilder.forJob("t", "d", ProfessionalArea.DATA, WorkModel.ON_SITE, skills,
						skills, 4000));
	}
}
