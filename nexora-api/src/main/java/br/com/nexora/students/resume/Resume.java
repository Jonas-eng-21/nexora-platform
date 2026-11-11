package br.com.nexora.students.resume;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Currículo-base estruturado do estudante: única fonte de dados do matching e origem do snapshot das candidaturas.
 * <p>
 * É um documento de valor imutável (sem relações JPA nem dados de autoridade), por isso o mesmo tipo serve de
 * contrato da API e de formato persistido em JSONB. Mudanças de estrutura aqui afetam currículos já gravados
 * e snapshots: prefira acrescentar campos opcionais a renomear ou remover.
 */
public record Resume(
		@Size(max = 10) List<@Valid @NotNull Education> education,
		@Size(max = 20) List<@Valid @NotNull Experience> experiences,
		@Size(max = 20) List<@Valid @NotNull Project> projects,
		@Size(max = 30) List<@Valid @NotNull Course> courses,
		@Size(max = 30) List<@NotBlank @Size(max = 80) String> softSkills,
		@Size(max = 30) List<@NotBlank @Size(max = 80) String> interests,
		@Valid Links links) {

	private static final String URL = "^https?://\\S+$";
	private static final String URL_MESSAGE = "deve ser uma URL http(s)";
	private static final String YEAR_MONTH = "^\\d{4}-(0[1-9]|1[0-2])$";
	private static final String YEAR_MONTH_MESSAGE = "deve estar no formato AAAA-MM";

	/** Seções ausentes viram listas vazias, para a estrutura lida ser sempre a mesma. */
	public Resume {
		education = education == null ? List.of() : List.copyOf(education);
		experiences = experiences == null ? List.of() : List.copyOf(experiences);
		projects = projects == null ? List.of() : List.copyOf(projects);
		courses = courses == null ? List.of() : List.copyOf(courses);
		softSkills = softSkills == null ? List.of() : List.copyOf(softSkills);
		interests = interests == null ? List.of() : List.copyOf(interests);
		links = links == null ? new Links(null, null, null) : links;
	}

	public static Resume empty() {
		return new Resume(null, null, null, null, null, null, null);
	}

	public record Education(
			@NotBlank @Size(max = 150) String institution,
			@NotBlank @Size(max = 150) String course,
			@Size(max = 80) String degree,
			@NotNull @Min(1950) @Max(2100) Integer startYear,
			@Min(1950) @Max(2100) Integer endYear) {
	}

	public record Experience(
			@NotBlank @Size(max = 150) String company,
			@NotBlank @Size(max = 150) String role,
			@Size(max = 2000) String description,
			@NotBlank @Pattern(regexp = YEAR_MONTH, message = YEAR_MONTH_MESSAGE) String startDate,
			@Pattern(regexp = YEAR_MONTH, message = YEAR_MONTH_MESSAGE) String endDate) {
	}

	public record Project(
			@NotBlank @Size(max = 150) String name,
			@Size(max = 2000) String description,
			@Size(max = 300) @Pattern(regexp = URL, message = URL_MESSAGE) String url) {
	}

	public record Course(
			@NotBlank @Size(max = 150) String name,
			@Size(max = 150) String institution,
			@Min(1950) @Max(2100) Integer year,
			@Size(max = 300) @Pattern(regexp = URL, message = URL_MESSAGE) String certificateUrl) {
	}

	public record Links(
			@Size(max = 300) @Pattern(regexp = URL, message = URL_MESSAGE) String github,
			@Size(max = 300) @Pattern(regexp = URL, message = URL_MESSAGE) String linkedin,
			@Size(max = 300) @Pattern(regexp = URL, message = URL_MESSAGE) String portfolio) {
	}
}
