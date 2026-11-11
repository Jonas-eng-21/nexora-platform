package br.com.nexora.universities.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.shared.security.AuthenticatedUser;
import br.com.nexora.universities.dto.UniversityProfileRequest;
import br.com.nexora.universities.dto.UniversityProfileResponse;
import br.com.nexora.universities.service.UniversityProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Só universidades ACTIVE chegam aqui: contas pendentes ou suspensas são barradas na autenticação. */
@RestController
@RequestMapping("/api/v1/universities/me")
@PreAuthorize("hasRole('UNIVERSITY')")
@Tag(name = "Universidade")
public class UniversityProfileController {

	private final UniversityProfileService universityProfileService;

	public UniversityProfileController(UniversityProfileService universityProfileService) {
		this.universityProfileService = universityProfileService;
	}

	@GetMapping
	@Operation(summary = "Consultar o próprio perfil institucional")
	public UniversityProfileResponse getProfile(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
		return UniversityProfileResponse.from(universityProfileService.getByUserId(principal.id()));
	}

	@PutMapping
	@Operation(summary = "Atualizar o próprio perfil institucional",
			description = "Substituição integral: campos opcionais ausentes são limpos.")
	public UniversityProfileResponse updateProfile(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody UniversityProfileRequest request) {
		return UniversityProfileResponse.from(universityProfileService.updateOwnProfile(principal.id(), request));
	}
}
