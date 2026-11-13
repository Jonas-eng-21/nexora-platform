package br.com.nexora.applications.dto;

import br.com.nexora.applications.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeApplicationStatusRequest(
		@Schema(description = "Novo andamento: IN_REVIEW, APPROVED ou REJECTED (SUSPENDED não pode ser atribuído)")
		@NotNull ApplicationStatus status,
		@Schema(description = "Motivo da decisão, até 500 caracteres. Obrigatório ao rejeitar; opcional nos demais.")
		@Size(max = 500) String reason) {
}
