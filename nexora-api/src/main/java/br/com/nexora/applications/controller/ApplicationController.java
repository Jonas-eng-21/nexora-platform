package br.com.nexora.applications.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.applications.dto.ApplicationResponse;
import br.com.nexora.applications.dto.CreateApplicationRequest;
import br.com.nexora.applications.service.ApplicationService;
import br.com.nexora.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/applications")
@Tag(name = "Candidaturas")
public class ApplicationController {

	private final ApplicationService applicationService;

	public ApplicationController(ApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('STUDENT')")
	@Operation(summary = "Candidatar-se a uma vaga com o currículo da plataforma",
			description = "Grava um snapshot imutável do perfil, do currículo-base e das competências do catálogo; "
					+ "alterações posteriores no currículo não o modificam. Exige vínculo acadêmico aprovado com "
					+ "universidade ativa (403 STUDENT_NOT_VALIDATED) e vaga aberta de empresa ativa "
					+ "(404 JOB_NOT_FOUND; 409 JOB_NOT_ACCEPTING_APPLICATIONS). Uma candidatura por vaga "
					+ "(409 APPLICATION_ALREADY_EXISTS). 404 STUDENT_PROFILE_NOT_FOUND sem perfil.")
	public ApplicationResponse apply(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID jobId, @Valid @RequestBody CreateApplicationRequest request) {
		return applicationService.apply(principal.id(), jobId, request);
	}
}
