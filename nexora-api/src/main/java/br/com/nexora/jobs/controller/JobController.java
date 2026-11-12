package br.com.nexora.jobs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.jobs.dto.CreateJobRequest;
import br.com.nexora.jobs.dto.JobResponse;
import br.com.nexora.jobs.service.JobService;
import br.com.nexora.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Vagas")
public class JobController {

	private final JobService jobService;

	public JobController(JobService jobService) {
		this.jobService = jobService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('COMPANY')")
	@Operation(summary = "Publicar uma vaga da própria empresa",
			description = "A empresa é a da conta autenticada (404 COMPANY_PROFILE_NOT_FOUND sem perfil). "
					+ "Competências inexistentes: 400 SKILL_NOT_FOUND; a mesma competência como obrigatória "
					+ "e desejável: 400 JOB_SKILL_CONFLICT. Status padrão: OPEN.")
	public JobResponse create(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody CreateJobRequest request) {
		return jobService.create(principal.id(), request);
	}
}

