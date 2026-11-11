package br.com.nexora.universities.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record AffiliationDecisionRequest(
		@Schema(description = "Observação opcional registrada na decisão")
		@Size(max = 500) String reason) {
}
