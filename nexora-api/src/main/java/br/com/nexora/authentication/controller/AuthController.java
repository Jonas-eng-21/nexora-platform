package br.com.nexora.authentication.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.authentication.dto.LoginRequest;
import br.com.nexora.authentication.dto.LoginResponse;
import br.com.nexora.authentication.dto.RegisterRequest;
import br.com.nexora.authentication.dto.RegisterResponse;
import br.com.nexora.authentication.service.LoginService;
import br.com.nexora.authentication.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação")
@SecurityRequirements // rotas públicas: sem exigência de token no contrato
public class AuthController {

	private final RegistrationService registrationService;
	private final LoginService loginService;

	public AuthController(RegistrationService registrationService, LoginService loginService) {
		this.registrationService = registrationService;
		this.loginService = loginService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Autocadastro de estudante, empresa ou universidade",
			description = "Estudante e empresa ficam ACTIVE; universidade fica PENDING_APPROVAL até aprovação do administrador.")
	public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
		return RegisterResponse.from(registrationService.register(request));
	}

	@PostMapping("/login")
	@Operation(summary = "Login com e-mail e senha",
			description = "Devolve um JWT expirável. Credenciais inválidas: 401 INVALID_CREDENTIALS. "
					+ "Conta suspensa ou aguardando aprovação: 403 ACCOUNT_SUSPENDED / ACCOUNT_PENDING_APPROVAL.")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return loginService.login(request);
	}
}
