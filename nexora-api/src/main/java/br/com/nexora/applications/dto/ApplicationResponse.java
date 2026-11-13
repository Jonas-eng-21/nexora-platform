package br.com.nexora.applications.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.entity.ResumeType;
import io.swagger.v3.oas.annotations.media.Schema;

/** Montado dentro da transação, pois vaga e empresa são carregadas sob demanda. */
public record ApplicationResponse(UUID id, UUID jobId, String jobTitle, String companyName, ResumeType resumeType,
		@Schema(description = "Nome original (já saneado) do PDF enviado; nulo na candidatura PLATFORM")
		String resumeFileName,
		@Schema(description = "Tamanho do PDF em bytes; nulo na candidatura PLATFORM") Long resumeFileSize,
		ApplicationStatus status, Instant createdAt) {

	public static ApplicationResponse from(Application application) {
		return new ApplicationResponse(application.getId(), application.getJob().getId(),
				application.getJob().getTitle(), application.getJob().getCompany().getName(),
				application.getResumeType(), application.getOriginalFilename(), application.getFileSize(),
				application.getStatus(), application.getCreatedAt());
	}
}
