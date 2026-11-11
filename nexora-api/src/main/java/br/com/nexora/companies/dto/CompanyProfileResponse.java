package br.com.nexora.companies.dto;

import java.util.UUID;

import br.com.nexora.companies.entity.Company;

public record CompanyProfileResponse(UUID id, String name, String sector, String description, String location) {

	public static CompanyProfileResponse from(Company company) {
		return new CompanyProfileResponse(company.getId(), company.getName(), company.getSector(),
				company.getDescription(), company.getLocation());
	}
}
