package br.com.nexora.authentication.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.authentication.dto.RegisterRequest;
import br.com.nexora.authentication.dto.RegisterResponse;
import br.com.nexora.authentication.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação")
public class AuthController {

	private final RegistrationService registrationService;

	public AuthController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirements // rota pública: sem exigência de token no contrato
	@Operation(summary = "Autocadastro de estudante, empresa ou universidade",
			description = "Estudante e empresa ficam ACTIVE; universidade fica PENDING_APPROVAL até aprovação do administrador.")
	public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
		return RegisterResponse.from(registrationService.register(request));
	}
}
