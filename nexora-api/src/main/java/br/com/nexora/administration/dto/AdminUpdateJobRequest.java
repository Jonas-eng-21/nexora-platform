package br.com.nexora.administration.dto;

import br.com.nexora.jobs.dto.CreateJobRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUpdateJobRequest(
		@NotNull @Valid CreateJobRequest job,
		@Schema(description = "Motivo da intervenção, registrado na auditoria")
		@NotBlank @Size(max = 500) String reason) {
}

