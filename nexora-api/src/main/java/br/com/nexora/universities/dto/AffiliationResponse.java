package br.com.nexora.universities.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.nexora.universities.entity.AffiliationStatus;
import br.com.nexora.universities.entity.UniversityAffiliation;

/** Visão do estudante sobre um pedido de vínculo. */
public record AffiliationResponse(UUID id, UniversitySummaryResponse university, AffiliationStatus status,
		Instant requestedAt) {

	public static AffiliationResponse from(UniversityAffiliation affiliation) {
		return new AffiliationResponse(affiliation.getId(),
				UniversitySummaryResponse.from(affiliation.getUniversity()), affiliation.getStatus(),
				affiliation.getRequestedAt());
	}
}
