package br.com.nexora.applications.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.entity.ResumeType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Item da lista de candidaturas do estudante. De propósito, não traz o snapshot nem dados do PDF: o documento
 * enviado é lido em {@code GET /api/v1/applications/{id}/resume}. Montado dentro da transação.
 */
public record ApplicationSummaryResponse(UUID id, UUID jobId, String jobTitle, String companyName,
		ApplicationStatus status, ResumeType resumeType,
		@Schema(description = "Quando a candidatura foi enviada") Instant appliedAt, Instant updatedAt) {

	public static ApplicationSummaryResponse from(Application application) {
		return new ApplicationSummaryResponse(application.getId(), application.getJob().getId(),
				application.getJob().getTitle(), application.getJob().getCompany().getName(),
				application.getStatus(), application.getResumeType(), application.getCreatedAt(),
				application.getUpdatedAt());
	}
}
