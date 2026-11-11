package br.com.nexora.users.service;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;

import br.com.nexora.shared.exception.ApiException;

/** Regra de senha que o Bean Validation não cobre, usada no cadastro e na troca de senha. */
public final class PasswordPolicy {

	private static final int BCRYPT_MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	/** O BCrypt considera apenas os primeiros 72 bytes; rejeitar em vez de truncar em silêncio. */
	public static void requireHashable(String rawPassword) {
		if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "A senha é longa demais.");
		}
	}
}
