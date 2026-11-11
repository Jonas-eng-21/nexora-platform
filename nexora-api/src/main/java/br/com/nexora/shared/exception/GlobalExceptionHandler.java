package br.com.nexora.shared.exception;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import br.com.nexora.shared.security.AccountNotActiveException;

/**
 * Ponto único de tradução de exceções para ProblemDetail (application/problem+json).
 * Toda resposta de erro carrega um "code" estável para o frontend.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private static final String CODE = "code";

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApiException(ApiException ex) {
		return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
	}

	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail handleAuthentication(AuthenticationException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Autenticação necessária.");
	}

	@ExceptionHandler(AccountNotActiveException.class)
	ProblemDetail handleAccountNotActive(AccountNotActiveException ex) {
		String detail = switch (ex.getStatus()) {
			case SUSPENDED -> "Sua conta está suspensa.";
			case PENDING_APPROVAL -> "Sua conta aguarda aprovação do administrador.";
			case REJECTED -> "Seu cadastro foi recusado pelo administrador.";
			case ACTIVE -> throw new IllegalStateException("Active account reported as not active", ex);
		};
		return problem(HttpStatus.FORBIDDEN, "ACCOUNT_" + ex.getStatus().name(), detail);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Você não tem permissão para realizar esta operação.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		// A mensagem do banco pode conter dados pessoais (ex.: e-mail duplicado): não logar nem devolver.
		log.warn("Data integrity violation ({})", ex.getMostSpecificCause().getClass().getSimpleName());
		return problem(HttpStatus.CONFLICT, "DATA_CONFLICT", "A operação conflita com dados já existentes.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro interno. Tente novamente mais tarde.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
				.toList();
		ProblemDetail body = ex.getBody();
		body.setDetail("Um ou mais campos são inválidos.");
		body.setProperty(CODE, "VALIDATION_ERROR");
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/** Erros padrão do Spring MVC (JSON malformado, rota inexistente etc.) recebem o nome do status como code. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem
				&& (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
			HttpStatus status = HttpStatus.resolve(statusCode.value());
			problem.setProperty(CODE, status != null ? status.name() : "ERROR");
		}
		return response;
	}

	private static ProblemDetail problem(HttpStatus status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty(CODE, code);
		return problem;
	}
}
