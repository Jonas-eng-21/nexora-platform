package br.com.nexora.users.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;

public record UserResponse(UUID id, String email, UserRole role, AccountStatus status, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getStatus(), user.getCreatedAt());
	}
}
