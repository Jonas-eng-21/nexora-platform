package br.com.nexora.matching.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.matching.dto.JobMatchResponse;
import br.com.nexora.matching.service.MatchingService;
import br.com.nexora.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/matching")
@Tag(name = "Matching")
public class MatchingController {

	private final MatchingService matchingService;

	public MatchingController(MatchingService matchingService) {
		this.matchingService = matchingService;
	}

	@GetMapping("/jobs/{jobId}")
	@PreAuthorize("hasRole('STUDENT')")
	@Operation(summary = "Calcular a própria compatibilidade com uma vaga",
			description = "Score de 0 a 100 calculado pelo sistema a partir do currículo-base e das competências do "
					+ "catálogo, com pontuação por critério, competências aderentes e ausentes e justificativa. "
					+ "Um PDF anexado a candidaturas nunca entra no cálculo. Os pesos são uma proposta de "
					+ "implementação, não uma medida validada cientificamente. "
					+ "404 JOB_NOT_FOUND se a vaga não está aberta; 404 STUDENT_PROFILE_NOT_FOUND sem perfil.")
	public JobMatchResponse matchWithJob(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID jobId) {
		return matchingService.matchOwnProfileWithJob(principal.id(), jobId);
	}
}
