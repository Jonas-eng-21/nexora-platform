package br.com.nexora.applications.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.entity.ResumeType;
import br.com.nexora.matching.dto.StudentSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Item da lista de candidaturas de uma vaga, para a empresa dona e o admin. Traz o resumo autorizado do estudante
 * (sem e-mail) e a compatibilidade atual, mas nunca o currículo enviado: ele é lido em
 * {@code GET /api/v1/applications/{id}/resume}.
 */
public record JobApplicationResponse(UUID id, ApplicationStatus status, ResumeType resumeType,
		@Schema(description = "Quando a candidatura foi enviada") Instant appliedAt, Instant updatedAt,
		StudentSummary student,
		@Schema(description = "Compatibilidade ATUAL (0 a 100) entre o currículo-base do estudante e a vaga, "
				+ "não a do momento do envio. Nunca considera o PDF. Nulo quando o currículo-base não tem "
				+ "competências nem áreas de interesse para comparar.", nullable = true)
		Integer matchScore) {

	public static JobApplicationResponse from(Application application, Integer matchScore) {
		return new JobApplicationResponse(application.getId(), application.getStatus(),
				application.getResumeType(), application.getCreatedAt(), application.getUpdatedAt(),
				StudentSummary.from(application.getStudent()), matchScore);
	}
}
