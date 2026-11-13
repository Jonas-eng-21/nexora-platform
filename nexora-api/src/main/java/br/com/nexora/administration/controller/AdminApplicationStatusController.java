package br.com.nexora.administration.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.administration.service.AdminApplicationService;
import br.com.nexora.applications.dto.ApplicationStatusResponse;
import br.com.nexora.applications.dto.ChangeApplicationStatusRequest;
import br.com.nexora.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Todas as rotas sob /api/v1/admin exigem papel ADMIN (regra no SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/applications")
@Tag(name = "Administração")
public class AdminApplicationStatusController {

	private final AdminApplicationService adminApplicationService;

	public AdminApplicationStatusController(AdminApplicationService adminApplicationService) {
		this.adminApplicationService = adminApplicationService;
	}

	@PatchMapping("/{applicationId}/status")
	@Operation(summary = "Intervir no andamento de uma candidatura",
			description = "As mesmas transições e regras da empresa (IN_REVIEW, APPROVED, REJECTED; finais e "
					+ "suspensas não mudam; estudante precisa estar academicamente elegível). Rejeitar exige motivo. "
					+ "Ação auditada com status anterior, novo status e motivo, na mesma transação.")
	public ApplicationStatusResponse changeStatus(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID applicationId, @Valid @RequestBody ChangeApplicationStatusRequest request) {
		return adminApplicationService.changeStatus(admin.id(), applicationId, request);
	}
}
