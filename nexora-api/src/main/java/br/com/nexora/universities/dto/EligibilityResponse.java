package br.com.nexora.universities.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record EligibilityResponse(
		@Schema(description = "true quando há vínculo acadêmico aprovado e vigente com universidade ativa")
		boolean eligible) {
}
