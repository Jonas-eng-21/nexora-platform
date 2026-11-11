package br.com.nexora.universities.dto;

import java.util.UUID;

import br.com.nexora.universities.entity.University;

public record UniversityProfileResponse(UUID id, String name, String acronym, String location, String website,
		String description) {

	public static UniversityProfileResponse from(University university) {
		return new UniversityProfileResponse(university.getId(), university.getName(), university.getAcronym(),
				university.getLocation(), university.getWebsite(), university.getDescription());
	}
}
