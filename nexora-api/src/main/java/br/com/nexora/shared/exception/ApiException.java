package br.com.nexora.shared.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Erro de negócio com status HTTP e código estável consumido pelo frontend.
 * A mensagem é exibida ao usuário: não incluir dados internos ou sensíveis.
 */
@Getter
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code, String detail) {
		super(detail);
		this.status = status;
		this.code = code;
	}
}
