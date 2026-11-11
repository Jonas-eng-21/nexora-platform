package br.com.nexora.universities.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectAffiliationRequest(
		@Schema(description = "Motivo da rejeição, exibido ao estudante")
		@NotBlank @Size(max = 500) String reason) {
}
