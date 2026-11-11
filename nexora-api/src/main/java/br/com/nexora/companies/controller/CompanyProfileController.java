package br.com.nexora.companies.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.companies.dto.CompanyProfileResponse;
import br.com.nexora.companies.dto.UpdateCompanyProfileRequest;
import br.com.nexora.companies.service.CompanyProfileService;
import br.com.nexora.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/companies/me")
@PreAuthorize("hasRole('COMPANY')")
@Tag(name = "Empresa")
public class CompanyProfileController {

	private final CompanyProfileService companyProfileService;

	public CompanyProfileController(CompanyProfileService companyProfileService) {
		this.companyProfileService = companyProfileService;
	}

	@GetMapping
	@Operation(summary = "Consultar o próprio perfil de empresa",
			description = "404 COMPANY_PROFILE_NOT_FOUND enquanto o perfil não for preenchido.")
	public CompanyProfileResponse getProfile(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
		return CompanyProfileResponse.from(companyProfileService.getByUserId(principal.id()));
	}

	@PutMapping
	@Operation(summary = "Criar ou atualizar o próprio perfil de empresa",
			description = "Substituição integral: campos opcionais ausentes são limpos.")
	public CompanyProfileResponse saveProfile(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody UpdateCompanyProfileRequest request) {
		return CompanyProfileResponse.from(companyProfileService.saveOwnProfile(principal.id(), request));
	}
}
