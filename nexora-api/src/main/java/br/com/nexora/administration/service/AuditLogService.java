package br.com.nexora.administration.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.administration.entity.AdminAction;
import br.com.nexora.administration.entity.AdminAuditLog;
import br.com.nexora.administration.entity.AuditTargetType;
import br.com.nexora.administration.repository.AdminAuditLogRepository;

@Service
public class AuditLogService {

	private final AdminAuditLogRepository auditLogRepository;

	public AuditLogService(AdminAuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	/**
	 * Registra a ação na mesma transação da operação auditada: se a operação falhar, o registro
	 * também é desfeito, e não existe ação crítica concluída sem rastro.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID adminId, AdminAction action, AuditTargetType targetType, UUID targetId, String reason) {
		record(adminId, action, targetType, targetId, reason, null, null);
	}

	/** Como a anterior, para ações que mudam um estado: guarda também o valor anterior e o novo. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID adminId, AdminAction action, AuditTargetType targetType, UUID targetId, String reason,
			String previousValue, String newValue) {
		auditLogRepository.save(
				new AdminAuditLog(adminId, action, targetType, targetId, reason, previousValue, newValue));
	}

	@Transactional(readOnly = true)
	public Page<AdminAuditLog> list(Pageable pageable) {
		return auditLogRepository.findAll(pageable);
	}
}
