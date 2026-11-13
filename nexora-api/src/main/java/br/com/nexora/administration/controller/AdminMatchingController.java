package br.com.nexora.administration.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.matching.dto.RecommendedStudentResponse;
import br.com.nexora.matching.service.MatchingService;
import br.com.nexora.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Todas as rotas sob /api/v1/admin exigem papel ADMIN (regra no SecurityConfig). Somente leitura: sem auditoria. */
@RestController
@RequestMapping("/api/v1/admin/jobs")
@Tag(name = "Administração")
public class AdminMatchingController {

	private final MatchingService matchingService;

	public AdminMatchingController(MatchingService matchingService) {
		this.matchingService = matchingService;
	}

	@GetMapping("/{jobId}/matching/students")
	@Operation(summary = "Listar estudantes recomendados para qualquer vaga",
			description = "Mesma lista e mesmos dados autorizados que a empresa dona vê. 404 JOB_NOT_FOUND para "
					+ "vaga inexistente ou excluída.")
	public PageResponse<RecommendedStudentResponse> recommendStudents(@PathVariable UUID jobId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return matchingService.recommendStudentsForJob(jobId, page, size);
	}
}
