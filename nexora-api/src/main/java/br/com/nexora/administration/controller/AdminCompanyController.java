package br.com.nexora.administration.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.administration.dto.AdminUpdateCompanyRequest;
import br.com.nexora.administration.service.AdminCompanyService;
import br.com.nexora.companies.dto.CompanyProfileResponse;
import br.com.nexora.companies.service.CompanyProfileService;
import br.com.nexora.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Todas as rotas sob /api/v1/admin exigem papel ADMIN (regra no SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/companies")
@Tag(name = "Administração")
public class AdminCompanyController {

	private final CompanyProfileService companyProfileService;
	private final AdminCompanyService adminCompanyService;

	public AdminCompanyController(CompanyProfileService companyProfileService,
			AdminCompanyService adminCompanyService) {
		this.companyProfileService = companyProfileService;
		this.adminCompanyService = adminCompanyService;
	}

	@GetMapping("/{companyId}")
	@Operation(summary = "Consultar o perfil de uma empresa")
	public CompanyProfileResponse getCompany(@PathVariable UUID companyId) {
		return CompanyProfileResponse.from(companyProfileService.getById(companyId));
	}

	@PutMapping("/{companyId}")
	@Operation(summary = "Intervir no perfil de uma empresa", description = "Exige motivo. Ação auditada.")
	public CompanyProfileResponse updateCompany(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID companyId, @Valid @RequestBody AdminUpdateCompanyRequest request) {
		return CompanyProfileResponse.from(adminCompanyService.updateProfile(admin.id(), companyId, request));
	}
}
