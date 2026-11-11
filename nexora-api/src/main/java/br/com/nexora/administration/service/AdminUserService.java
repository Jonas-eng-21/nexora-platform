package br.com.nexora.administration.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.administration.entity.AdminAction;
import br.com.nexora.administration.entity.AuditTargetType;
import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;

@Service
public class AdminUserService {

	private final UserRepository userRepository;
	private final AuditLogService auditLogService;

	public AdminUserService(UserRepository userRepository, AuditLogService auditLogService) {
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional(readOnly = true)
	public Page<User> list(UserRole role, AccountStatus status, Pageable pageable) {
		return userRepository.search(role, status, pageable);
	}

	@Transactional(readOnly = true)
	public User get(UUID userId) {
		return findUser(userId);
	}

	@Transactional
	public User suspend(UUID adminId, UUID userId, String reason) {
		if (adminId.equals(userId)) {
			// Evita que o último administrador se tranque para fora da plataforma.
			throw new ApiException(HttpStatus.CONFLICT, "CANNOT_SUSPEND_SELF",
					"Você não pode suspender a própria conta.");
		}
		User user = findUser(userId);
		requireStatus(user, AccountStatus.ACTIVE, "Apenas contas ativas podem ser suspensas.");
		user.changeStatus(AccountStatus.SUSPENDED);
		auditLogService.record(adminId, AdminAction.USER_SUSPENDED, AuditTargetType.USER, userId, reason);
		return user;
	}

	/**
	 * Só reativa contas SUSPENDED. Contas PENDING_APPROVAL ficam de fora de propósito:
	 * a aprovação de universidades tem fluxo próprio e não pode ser contornada por aqui.
	 */
	@Transactional
	public User reactivate(UUID adminId, UUID userId, String reason) {
		User user = findUser(userId);
		requireStatus(user, AccountStatus.SUSPENDED, "Apenas contas suspensas podem ser reativadas.");
		user.changeStatus(AccountStatus.ACTIVE);
		auditLogService.record(adminId, AdminAction.USER_REACTIVATED, AuditTargetType.USER, userId, reason);
		return user;
	}

	private static void requireStatus(User user, AccountStatus expected, String detail) {
		if (user.getStatus() != expected) {
			throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", detail);
		}
	}

	private User findUser(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado."));
	}
}
