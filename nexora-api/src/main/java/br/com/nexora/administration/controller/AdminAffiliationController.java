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
import br.com.nexora.administration.service.AdminAffiliationService;
import br.com.nexora.shared.dto.PageResponse;
import br.com.nexora.shared.security.AuthenticatedUser;
import br.com.nexora.universities.dto.ReceivedAffiliationResponse;
import br.com.nexora.universities.entity.AffiliationStatus;
import br.com.nexora.universities.service.AffiliationDecisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Todas as rotas sob /api/v1/admin exigem papel ADMIN (regra no SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/affiliations")
@Tag(name = "Administração")
public class AdminAffiliationController {

	private static final Sort OLDEST_FIRST = Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("id"));

	private final AffiliationDecisionService affiliationDecisionService;
	private final AdminAffiliationService adminAffiliationService;

	public AdminAffiliationController(AffiliationDecisionService affiliationDecisionService,
			AdminAffiliationService adminAffiliationService) {
		this.affiliationDecisionService = affiliationDecisionService;
		this.adminAffiliationService = adminAffiliationService;
	}

	@GetMapping
	@Operation(summary = "Listar pedidos de vínculo de todas as universidades")
	public PageResponse<ReceivedAffiliationResponse> list(
			@RequestParam(required = false) AffiliationStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(
				affiliationDecisionService.listAll(status, PageResponse.pageable(page, size, OLDEST_FIRST)),
				response -> response);
	}

	@PostMapping("/{affiliationId}/approve")
	@Operation(summary = "Intervir aprovando um pedido de vínculo", description = "Exige motivo. Ação auditada.")
	public ReceivedAffiliationResponse approve(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID affiliationId, @Valid @RequestBody AdminActionRequest request) {
		return adminAffiliationService.approve(admin.id(), affiliationId, request.reason());
	}

	@PostMapping("/{affiliationId}/revoke")
	@Operation(summary = "Intervir revogando um vínculo aprovado", description = "Exige motivo. Ação auditada.")
	public ReceivedAffiliationResponse revoke(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID affiliationId, @Valid @RequestBody AdminActionRequest request) {
		return adminAffiliationService.revoke(admin.id(), affiliationId, request.reason());
	}

	@PostMapping("/{affiliationId}/reject")
	@Operation(summary = "Intervir rejeitando um pedido de vínculo", description = "Exige motivo. Ação auditada.")
	public ReceivedAffiliationResponse reject(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID affiliationId, @Valid @RequestBody AdminActionRequest request) {
		return adminAffiliationService.reject(admin.id(), affiliationId, request.reason());
	}
}
