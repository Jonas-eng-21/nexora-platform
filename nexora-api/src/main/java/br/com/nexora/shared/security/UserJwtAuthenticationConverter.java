package br.com.nexora.shared.security;

import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.repository.UserRepository;

/**
 * Converte um JWT já validado (assinatura e expiração) na identidade autenticada.
 * Papel e status vêm do banco a cada requisição: conta não ativa é bloqueada mesmo
 * com token dentro da validade.
 */
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserRepository userRepository;

	public UserJwtAuthenticationConverter(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		User user = userRepository.findById(parseUserId(jwt.getSubject()))
				.orElseThrow(() -> new BadCredentialsException("Token subject does not match a user"));
		if (user.getStatus() != AccountStatus.ACTIVE) {
			throw new AccountNotActiveException(user.getStatus());
		}
		return UsernamePasswordAuthenticationToken.authenticated(
				new AuthenticatedUser(user.getId(), user.getRole()),
				null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}

	private static UUID parseUserId(String subject) {
		try {
			return UUID.fromString(subject);
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			throw new BadCredentialsException("Token subject is not a valid user id");
		}
	}
}
