package br.com.nexora.universities.dto;

import java.util.UUID;

import br.com.nexora.universities.entity.University;

/** Dados públicos da universidade para usuários autenticados: sem e-mail, conta ou análise de aprovação. */
public record UniversitySummaryResponse(UUID id, String name, String acronym, String location, String website) {

	public static UniversitySummaryResponse from(University university) {
		return new UniversitySummaryResponse(university.getId(), university.getName(), university.getAcronym(),
				university.getLocation(), university.getWebsite());
	}
}
