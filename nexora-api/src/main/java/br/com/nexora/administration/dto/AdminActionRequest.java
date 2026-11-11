package br.com.nexora.administration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminActionRequest(
		@Schema(description = "Motivo da intervenção, registrado na auditoria")
		@NotBlank @Size(max = 500) String reason) {
}
