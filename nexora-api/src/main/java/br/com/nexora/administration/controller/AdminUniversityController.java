package br.com.nexora.administration.controller;

import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.administration.dto.AdminActionRequest;
import br.com.nexora.administration.dto.AdminUniversityResponse;
import br.com.nexora.administration.dto.ApproveUniversityRequest;
import br.com.nexora.administration.service.AdminUniversityService;
import br.com.nexora.shared.dto.PageResponse;
import br.com.nexora.shared.security.AuthenticatedUser;
import br.com.nexora.universities.entity.UniversityApprovalStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Todas as rotas sob /api/v1/admin exigem papel ADMIN (regra no SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/universities")
@Tag(name = "Administração")
public class AdminUniversityController {

	private static final Sort OLDEST_FIRST = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

	private final AdminUniversityService adminUniversityService;

	public AdminUniversityController(AdminUniversityService adminUniversityService) {
		this.adminUniversityService = adminUniversityService;
	}

	@GetMapping
	@Operation(summary = "Listar universidades, da mais antiga para a mais recente",
			description = "Use approvalStatus=PENDING para a fila de cadastros aguardando análise.")
	public PageResponse<AdminUniversityResponse> list(
			@RequestParam(required = false) UniversityApprovalStatus approvalStatus,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(
				adminUniversityService.list(approvalStatus, PageResponse.pageable(page, size, OLDEST_FIRST)),
				response -> response);
	}

	@GetMapping("/{universityId}")
	@Operation(summary = "Consultar uma universidade")
	public AdminUniversityResponse get(@PathVariable UUID universityId) {
		return adminUniversityService.get(universityId);
	}

	@PostMapping("/{universityId}/approve")
	@Operation(summary = "Aprovar o cadastro de uma universidade pendente",
			description = "A conta passa a ACTIVE. Cadastro já analisado: 409 UNIVERSITY_ALREADY_DECIDED. Ação auditada.")
	public AdminUniversityResponse approve(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID universityId, @Valid @RequestBody ApproveUniversityRequest request) {
		return adminUniversityService.approve(admin.id(), universityId, request.reason());
	}

	@PostMapping("/{universityId}/reject")
	@Operation(summary = "Rejeitar o cadastro de uma universidade pendente",
			description = "Exige motivo. A conta passa a REJECTED e não recebe poder de aprovação. Ação auditada.")
	public AdminUniversityResponse reject(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID universityId, @Valid @RequestBody AdminActionRequest request) {
		return adminUniversityService.reject(admin.id(), universityId, request.reason());
	}
}
