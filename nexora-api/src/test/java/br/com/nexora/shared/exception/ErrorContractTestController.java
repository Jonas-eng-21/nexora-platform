package br.com.nexora.shared.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Controller existente apenas nos testes, para exercitar o contrato de erros. */
@RestController
@RequestMapping("/test-errors")
class ErrorContractTestController {

	record SampleRequest(@NotBlank String name, @NotBlank @Email String email) {
	}

	@PostMapping("/validation")
	String validation(@Valid @RequestBody SampleRequest request) {
		return request.name();
	}

	@GetMapping("/forbidden")
	String forbidden() {
		throw new AccessDeniedException("internal reason");
	}

	@GetMapping("/not-found")
	String notFound() {
		throw new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Vaga não encontrada.");
	}

	@GetMapping("/conflict")
	String conflict() {
		throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "E-mail já cadastrado.");
	}

	@GetMapping("/data-conflict")
	String dataConflict() {
		throw new DataIntegrityViolationException("Key (email)=(secret@nexora.test) already exists");
	}

	@GetMapping("/unexpected")
	String unexpected() {
		throw new IllegalStateException("secret-internal-detail");
	}
}
