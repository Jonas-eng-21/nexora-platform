package br.com.nexora.matching.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.matching.dto.JobMatchResponse;
import br.com.nexora.matching.dto.RecommendedJobResponse;
import br.com.nexora.matching.dto.RecommendedStudentResponse;
import br.com.nexora.matching.service.MatchingService;
import br.com.nexora.shared.dto.PageResponse;
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

	@GetMapping("/jobs")
	@PreAuthorize("hasRole('STUDENT')")
	@Operation(summary = "Listar vagas recomendadas, da mais para a menos compatível",
			description = "Considera apenas vagas abertas de empresas ativas e o currículo-base do próprio estudante. "
					+ "Cada item traz a vaga e a compatibilidade com pontuação por critério e justificativa. "
					+ "No empate de score, a vaga mais recente vem primeiro. São avaliadas as 500 vagas abertas "
					+ "mais recentes. 404 STUDENT_PROFILE_NOT_FOUND sem perfil.")
	public PageResponse<RecommendedJobResponse> recommendJobs(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return matchingService.recommendJobs(principal.id(), page, size);
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

	@GetMapping("/jobs/{jobId}/students")
	@PreAuthorize("hasRole('COMPANY')")
	@Operation(summary = "Listar estudantes recomendados para uma vaga da própria empresa",
			description = "Considera apenas estudantes academicamente validados (vínculo aprovado com universidade "
					+ "ativa) e com conta ativa, ordenados do mais para o menos compatível; no empate, o perfil mais "
					+ "recente vem primeiro. A empresa vê nome, curso, localização, resumo, competências e áreas de "
					+ "interesse, nunca e-mail nem o currículo completo. Cada item traz a pontuação por critério e a "
					+ "justificativa. São avaliados os 500 perfis elegíveis mais recentes. "
					+ "404 JOB_NOT_FOUND para vaga inexistente, excluída ou de outra empresa; "
					+ "404 COMPANY_PROFILE_NOT_FOUND sem perfil de empresa.")
	public PageResponse<RecommendedStudentResponse> recommendStudents(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID jobId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return matchingService.recommendStudentsForOwnJob(principal.id(), jobId, page, size);
	}
}
