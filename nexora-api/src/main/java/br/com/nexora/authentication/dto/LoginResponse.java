package br.com.nexora.authentication.dto;

import br.com.nexora.users.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
		String accessToken,
		@Schema(example = "Bearer") String tokenType,
		@Schema(description = "Validade do token, em segundos") long expiresIn,
		@Schema(description = "Papel da conta, para o frontend direcionar ao painel correto") UserRole role) {
}
