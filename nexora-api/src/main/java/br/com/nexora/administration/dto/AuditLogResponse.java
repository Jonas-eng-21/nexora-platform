package br.com.nexora.administration.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.nexora.administration.entity.AdminAction;
import br.com.nexora.administration.entity.AdminAuditLog;
import br.com.nexora.administration.entity.AuditTargetType;

public record AuditLogResponse(UUID id, UUID adminId, AdminAction action, AuditTargetType targetType, UUID targetId,
		String reason, String previousValue, String newValue, Instant createdAt) {

	public static AuditLogResponse from(AdminAuditLog log) {
		return new AuditLogResponse(log.getId(), log.getAdminId(), log.getAction(), log.getTargetType(),
				log.getTargetId(), log.getReason(), log.getPreviousValue(), log.getNewValue(), log.getCreatedAt());
	}
}
