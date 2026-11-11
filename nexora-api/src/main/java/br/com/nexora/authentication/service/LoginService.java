package br.com.nexora.authentication.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.authentication.dto.LoginRequest;
import br.com.nexora.authentication.dto.LoginResponse;
import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.shared.security.AccountNotActiveException;
import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.repository.UserRepository;

@Service
public class LoginService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	// Comparado quando o e-mail não existe, para o tempo de resposta não revelar quais e-mails estão cadastrados.
	private final String dummyHash;

	public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.dummyHash = passwordEncoder.encode("nexora-dummy-password");
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(User.normalizeEmail(request.email())).orElse(null);
		String hash = user != null ? user.getPasswordHash() : dummyHash;
		// Mesma resposta para e-mail inexistente e senha errada.
		if (!passwordEncoder.matches(request.password(), hash) || user == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-mail ou senha inválidos.");
		}
		// O status só é informado a quem provou conhecer a senha.
		if (user.getStatus() != AccountStatus.ACTIVE) {
			throw new AccountNotActiveException(user.getStatus());
		}
		return new LoginResponse(tokenService.issue(user), "Bearer",
				tokenService.getExpiration().toSeconds(), user.getRole());
	}
}
