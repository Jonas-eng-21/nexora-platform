package br.com.nexora.universities.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.shared.security.AuthenticatedUser;
import br.com.nexora.universities.dto.AffiliationResponse;
import br.com.nexora.universities.dto.RequestAffiliationRequest;
import br.com.nexora.universities.service.AffiliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/affiliations")
@Tag(name = "Vínculo acadêmico")
public class AffiliationController {

	private final AffiliationService affiliationService;

	public AffiliationController(AffiliationService affiliationService) {
		this.affiliationService = affiliationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('STUDENT')")
	@Operation(summary = "Solicitar vínculo acadêmico com uma universidade ativa",
			description = "409 quando a universidade não está ativa (UNIVERSITY_NOT_ACTIVE), já existe solicitação "
					+ "pendente (AFFILIATION_ALREADY_PENDING) ou vínculo aprovado (AFFILIATION_ALREADY_APPROVED).")
	public AffiliationResponse request(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody RequestAffiliationRequest request) {
		return affiliationService.request(principal.id(), request.universityId());
	}

	@GetMapping("/me")
	@PreAuthorize("hasRole('STUDENT')")
	@Operation(summary = "Consultar o próprio histórico de pedidos de vínculo, do mais recente para o mais antigo")
	public List<AffiliationResponse> listOwn(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
		return affiliationService.listOwn(principal.id());
	}
}
