package br.com.nexora.administration.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.applications.dto.JobApplicationResponse;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.service.JobApplicationService;
import br.com.nexora.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Todas as rotas sob /api/v1/admin exigem papel ADMIN (regra no SecurityConfig). Somente leitura: sem auditoria. */
@RestController
@RequestMapping("/api/v1/admin/jobs")
@Tag(name = "Administração")
public class AdminApplicationController {

	private final JobApplicationService jobApplicationService;

	public AdminApplicationController(JobApplicationService jobApplicationService) {
		this.jobApplicationService = jobApplicationService;
	}

	@GetMapping("/{jobId}/applications")
	@Operation(summary = "Listar as candidaturas de qualquer vaga",
			description = "Mesma resposta, paginação e filtro que a empresa dona vê (tamanho padrão 10, máximo 50; "
					+ "filtro opcional status; todos os status). Somente leitura. 404 JOB_NOT_FOUND para vaga "
					+ "inexistente ou excluída.")
	public PageResponse<JobApplicationResponse> listApplications(@PathVariable UUID jobId,
			@RequestParam(required = false) ApplicationStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return jobApplicationService.listForJob(jobId, status, page, size);
	}
}
