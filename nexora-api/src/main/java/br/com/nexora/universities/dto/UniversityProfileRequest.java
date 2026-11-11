package br.com.nexora.universities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dados institucionais, usados no cadastro e na atualização (substituição integral). */
public record UniversityProfileRequest(
		@NotBlank @Size(max = 150) String name,
		@Size(max = 30) String acronym,
		@Size(max = 150) String location,
		@Size(max = 300) @Pattern(regexp = "^(https?://\\S+)?\\s*$", message = "deve ser uma URL http(s)") String website,
		@Size(max = 2000) String description) {
}
