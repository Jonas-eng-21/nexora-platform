package br.com.nexora.administration.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Registro imutável de uma ação administrativa: não possui métodos de alteração. */
@Entity
@Table(name = "admin_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "admin_id", nullable = false, updatable = false)
	private UUID adminId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 50)
	private AdminAction action;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, updatable = false, length = 30)
	private AuditTargetType targetType;

	@Column(name = "target_id", nullable = false, updatable = false)
	private UUID targetId;

	@Column(updatable = false, length = 500)
	private String reason;

	/** Valor antes e depois da ação, para ações que mudam um estado (ex.: status de candidatura). */
	@Column(name = "previous_value", updatable = false, length = 50)
	private String previousValue;

	@Column(name = "new_value", updatable = false, length = 50)
	private String newValue;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public AdminAuditLog(UUID adminId, AdminAction action, AuditTargetType targetType, UUID targetId, String reason) {
		this(adminId, action, targetType, targetId, reason, null, null);
	}

	public AdminAuditLog(UUID adminId, AdminAction action, AuditTargetType targetType, UUID targetId, String reason,
			String previousValue, String newValue) {
		this.adminId = adminId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.reason = reason;
		this.previousValue = previousValue;
		this.newValue = newValue;
	}
}
