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
import br.com.nexora.administration.dto.AuditLogResponse;
import br.com.nexora.administration.service.AdminUserService;
import br.com.nexora.administration.service.AuditLogService;
import br.com.nexora.shared.dto.PageResponse;
import br.com.nexora.shared.security.AuthenticatedUser;
import br.com.nexora.users.dto.UserResponse;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Todas as rotas sob /api/v1/admin exigem papel ADMIN (regra no SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administração")
public class AdminUserController {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

	private final AdminUserService adminUserService;
	private final AuditLogService auditLogService;

	public AdminUserController(AdminUserService adminUserService, AuditLogService auditLogService) {
		this.adminUserService = adminUserService;
		this.auditLogService = auditLogService;
	}

	@GetMapping("/users")
	@Operation(summary = "Listar contas, com filtros opcionais por papel e status")
	public PageResponse<UserResponse> listUsers(
			@RequestParam(required = false) UserRole role,
			@RequestParam(required = false) AccountStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(
				adminUserService.list(role, status, PageResponse.pageable(page, size, NEWEST_FIRST)),
				UserResponse::from);
	}

	@GetMapping("/users/{userId}")
	@Operation(summary = "Consultar uma conta")
	public UserResponse getUser(@PathVariable UUID userId) {
		return UserResponse.from(adminUserService.get(userId));
	}

	@PostMapping("/users/{userId}/suspend")
	@Operation(summary = "Suspender uma conta ativa",
			description = "Bloqueia todas as operações autenticadas da conta, mesmo com token ainda válido. Ação auditada.")
	public UserResponse suspendUser(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID userId, @Valid @RequestBody AdminActionRequest request) {
		return UserResponse.from(adminUserService.suspend(admin.id(), userId, request.reason()));
	}

	@PostMapping("/users/{userId}/reactivate")
	@Operation(summary = "Reativar uma conta suspensa",
			description = "Não se aplica a contas aguardando aprovação. Ação auditada.")
	public UserResponse reactivateUser(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin,
			@PathVariable UUID userId, @Valid @RequestBody AdminActionRequest request) {
		return UserResponse.from(adminUserService.reactivate(admin.id(), userId, request.reason()));
	}

	@GetMapping("/audit-logs")
	@Operation(summary = "Consultar a auditoria de ações administrativas, da mais recente para a mais antiga")
	public PageResponse<AuditLogResponse> listAuditLogs(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(auditLogService.list(PageResponse.pageable(page, size, NEWEST_FIRST)),
				AuditLogResponse::from);
	}
}
