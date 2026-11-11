package br.com.nexora.users.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.shared.security.AuthenticatedUser;
import br.com.nexora.users.dto.UpdateAccountRequest;
import br.com.nexora.users.dto.UserResponse;
import br.com.nexora.users.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Conta")
public class UserAccountController {

	private final UserAccountService userAccountService;

	public UserAccountController(UserAccountService userAccountService) {
		this.userAccountService = userAccountService;
	}

	@GetMapping
	@Operation(summary = "Consultar a própria conta")
	public UserResponse getAccount(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
		return UserResponse.from(userAccountService.getAccount(principal.id()));
	}

	@PatchMapping
	@Operation(summary = "Alterar e-mail e/ou senha da própria conta",
			description = "Exige a senha atual. Papel e status não podem ser alterados por esta rota.")
	public UserResponse updateAccount(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody UpdateAccountRequest request) {
		return UserResponse.from(userAccountService.updateAccount(principal.id(), request));
	}
}
