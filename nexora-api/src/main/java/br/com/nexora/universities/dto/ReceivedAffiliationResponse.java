package br.com.nexora.universities.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.nexora.students.entity.Student;
import br.com.nexora.universities.entity.AffiliationStatus;
import br.com.nexora.universities.entity.UniversityAffiliation;

/** Visão de quem analisa o pedido (universidade destinatária ou administrador). */
public record ReceivedAffiliationResponse(UUID id, StudentSummary student, UniversitySummaryResponse university,
		AffiliationStatus status, Instant requestedAt, Instant decidedAt, String decisionReason, Instant revokedAt,
		String revocationReason) {

	/** Apenas o necessário para a universidade reconhecer o estudante: sem e-mail nem currículo. */
	public record StudentSummary(UUID id, String fullName, String course, String institution) {

		static StudentSummary from(Student student) {
			return new StudentSummary(student.getId(), student.getFullName(), student.getCourse(),
					student.getInstitution());
		}
	}

	public static ReceivedAffiliationResponse from(UniversityAffiliation affiliation) {
		return new ReceivedAffiliationResponse(affiliation.getId(), StudentSummary.from(affiliation.getStudent()),
				UniversitySummaryResponse.from(affiliation.getUniversity()), affiliation.getStatus(),
				affiliation.getRequestedAt(), affiliation.getDecidedAt(), affiliation.getDecisionReason(),
				affiliation.getRevokedAt(), affiliation.getRevocationReason());
	}
}
