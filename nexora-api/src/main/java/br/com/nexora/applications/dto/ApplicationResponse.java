package br.com.nexora.applications.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.entity.ResumeType;

/** Montado dentro da transação, pois vaga e empresa são carregadas sob demanda. */
public record ApplicationResponse(UUID id, UUID jobId, String jobTitle, String companyName, ResumeType resumeType,
		ApplicationStatus status, Instant createdAt) {

	public static ApplicationResponse from(Application application) {
		return new ApplicationResponse(application.getId(), application.getJob().getId(),
				application.getJob().getTitle(), application.getJob().getCompany().getName(),
				application.getResumeType(), application.getStatus(), application.getCreatedAt());
	}
}
