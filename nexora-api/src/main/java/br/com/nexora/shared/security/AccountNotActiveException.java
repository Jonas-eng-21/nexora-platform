package br.com.nexora.shared.security;

import org.springframework.security.authentication.AccountStatusException;

import br.com.nexora.users.entity.AccountStatus;
import lombok.Getter;

/** Token válido, mas a conta não está ACTIVE (suspensa ou aguardando aprovação). */
@Getter
public class AccountNotActiveException extends AccountStatusException {

	private final AccountStatus status;

	public AccountNotActiveException(AccountStatus status) {
		super("Account is not active: " + status);
		this.status = status;
	}
}
