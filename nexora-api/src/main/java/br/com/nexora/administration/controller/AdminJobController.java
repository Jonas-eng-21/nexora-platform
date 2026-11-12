package br.com.nexora.administration.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.administration.dto.AdminActionRequest;
import br.com.nexora.administration.dto.AdminUpdateJobRequest;
import br.com.nexora.administration.service.AdminJobService;
import br.com.nexora.jobs.dto.JobResponse;
import br.com.nexora.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Todas as rotas sob /api/v1/admin exigem papel ADMIN (regra no SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/jobs")
@Tag(name = "Administração")
public class AdminJobController {

	private final AdminJobService adminJobService;

	public AdminJobController(AdminJobService adminJobService) {
		this.adminJobService = adminJobService;
	}

	@PutMapping("/{jobId}")
	@Operation(summary = "Intervir editando uma vaga", description = "Exige motivo. Ação auditada.")
	public JobResponse update(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID jobId, @Valid @RequestBody AdminUpdateJobRequest request) {
		return adminJobService.update(admin.id(), jobId, request);
	}

	@PostMapping("/{jobId}/close")
	@Operation(summary = "Intervir encerrando uma vaga", description = "Exige motivo. Ação auditada.")
	public JobResponse close(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID jobId, @Valid @RequestBody AdminActionRequest request) {
		return adminJobService.close(admin.id(), jobId, request.reason());
	}

	@PostMapping("/{jobId}/reopen")
	@Operation(summary = "Intervir reabrindo uma vaga encerrada", description = "Exige motivo. Ação auditada.")
	public JobResponse reopen(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID jobId, @Valid @RequestBody AdminActionRequest request) {
		return adminJobService.reopen(admin.id(), jobId, request.reason());
	}

	@DeleteMapping("/{jobId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Intervir excluindo logicamente uma vaga", description = "Exige motivo. Ação auditada.")
	public void delete(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID jobId, @Valid @RequestBody AdminActionRequest request) {
		adminJobService.delete(admin.id(), jobId, request.reason());
	}
}

