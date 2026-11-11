package br.com.nexora.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Campos ausentes (null) não são alterados. Papel e status não fazem parte deste contrato. */
public record UpdateAccountRequest(
		@Schema(description = "Senha atual, exigida para confirmar qualquer alteração")
		@NotBlank String currentPassword,
		@Schema(description = "Novo e-mail (opcional)")
		@Email @Size(min = 3, max = 254) String email,
		@Schema(description = "Nova senha (opcional)")
		@Size(min = 8, max = 72) String newPassword) {
}
