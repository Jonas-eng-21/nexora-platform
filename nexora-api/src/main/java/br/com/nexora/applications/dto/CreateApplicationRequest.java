package br.com.nexora.applications.dto;

import br.com.nexora.applications.entity.ResumeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CreateApplicationRequest(
		@Schema(description = "Documento enviado. Por ora, apenas PLATFORM: snapshot do currículo-base.")
		@NotNull ResumeType resumeType) {
}
