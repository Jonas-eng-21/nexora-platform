package br.com.nexora.students.dto;

import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStudentSkillsRequest(
		@Schema(description = "IDs de competências do catálogo. Substitui a seleção anterior; lista vazia limpa.")
		@NotNull @Size(max = 50) Set<@NotNull UUID> skillIds) {
}
