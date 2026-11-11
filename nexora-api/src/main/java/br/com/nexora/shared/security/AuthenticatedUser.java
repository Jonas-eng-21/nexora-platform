package br.com.nexora.shared.security;

import java.util.UUID;

import br.com.nexora.users.entity.UserRole;

/** Identidade do usuário autenticado, disponível via {@code @AuthenticationPrincipal}. */
public record AuthenticatedUser(UUID id, UserRole role) {
}
