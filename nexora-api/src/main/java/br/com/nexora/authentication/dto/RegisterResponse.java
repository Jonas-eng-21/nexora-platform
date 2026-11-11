package br.com.nexora.authentication.dto;

import java.util.UUID;

import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;

public record RegisterResponse(UUID id, String email, UserRole role, AccountStatus status) {

	public static RegisterResponse from(User user) {
		return new RegisterResponse(user.getId(), user.getEmail(), user.getRole(), user.getStatus());
	}
}
