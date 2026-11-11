package br.com.nexora.companies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Substituição integral do perfil: campos opcionais ausentes são limpos. */
public record UpdateCompanyProfileRequest(
		@NotBlank @Size(max = 150) String name,
		@Size(max = 150) String sector,
		@Size(max = 2000) String description,
		@Size(max = 150) String location) {
}
