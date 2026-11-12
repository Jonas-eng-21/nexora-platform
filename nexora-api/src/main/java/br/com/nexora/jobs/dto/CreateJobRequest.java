package br.com.nexora.jobs.dto;

import java.util.Set;
import java.util.UUID;

import br.com.nexora.jobs.entity.JobLevel;
import br.com.nexora.jobs.entity.JobStatus;
import br.com.nexora.shared.domain.ProfessionalArea;
import br.com.nexora.shared.domain.WorkModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A empresa dona não faz parte do contrato: ela vem do usuário autenticado. */
public record CreateJobRequest(
		@NotBlank @Size(max = 150) String title,
		@NotBlank @Size(max = 5000) String description,
		@Schema(description = "Área de atuação, do catálogo fixo (GET /api/v1/areas).")
		@NotNull ProfessionalArea area,
		@Size(max = 150) String location,
		@NotNull WorkModel workModel,
		@NotNull JobLevel level,
		@Schema(description = "Opcional; padrão OPEN.") JobStatus status,
		@Schema(description = "IDs do catálogo de competências. Ao menos uma.")
		@NotEmpty @Size(max = 30) Set<@NotNull UUID> requiredSkillIds,
		@Schema(description = "IDs do catálogo de competências. Não podem repetir as obrigatórias.")
		@Size(max = 30) Set<@NotNull UUID> desiredSkillIds) {
}

