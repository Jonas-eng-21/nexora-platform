package br.com.nexora.shared.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller existente apenas nos testes, para exercitar autenticação e RBAC. */
@RestController
class SecurityTestController {

	@GetMapping("/test-security/me")
	AuthenticatedUser me(@AuthenticationPrincipal AuthenticatedUser user) {
		return user;
	}

	@GetMapping("/test-security/company-only")
	@PreAuthorize("hasRole('COMPANY')")
	String companyOnly() {
		return "ok";
	}

	@GetMapping("/api/v1/admin/test-ping")
	String adminPing() {
		return "ok";
	}
}
