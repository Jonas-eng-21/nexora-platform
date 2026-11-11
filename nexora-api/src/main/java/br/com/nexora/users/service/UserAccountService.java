package br.com.nexora.users.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.users.dto.UpdateAccountRequest;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.repository.UserRepository;

@Service
public class UserAccountService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public User getAccount(UUID userId) {
		return findUser(userId);
	}

	/** Altera apenas a conta do id informado, que sempre vem do contexto de segurança. */
	@Transactional
	public User updateAccount(UUID userId, UpdateAccountRequest request) {
		User user = findUser(userId);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "A senha atual está incorreta.");
		}
		if (request.email() != null) {
			changeEmail(user, User.normalizeEmail(request.email()));
		}
		if (request.newPassword() != null) {
			PasswordPolicy.requireHashable(request.newPassword());
			user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
		}
		return user;
	}

	private void changeEmail(User user, String newEmail) {
		if (newEmail.equals(user.getEmail())) {
			return;
		}
		if (userRepository.existsByEmail(newEmail)) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "E-mail já cadastrado.");
		}
		user.changeEmail(newEmail);
	}

	private User findUser(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado."));
	}
}
