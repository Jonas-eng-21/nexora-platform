package br.com.nexora.administration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ApproveUniversityRequest(
		@Schema(description = "Observação opcional sobre a aprovação, registrada na decisão e na auditoria")
		@Size(max = 500) String reason) {
}
