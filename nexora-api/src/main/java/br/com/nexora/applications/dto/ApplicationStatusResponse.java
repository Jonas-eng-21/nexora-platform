package br.com.nexora.applications.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.entity.ApplicationStatus;

/** Resultado de uma mudança de andamento. Nunca traz o currículo enviado nem dados do estudante. */
public record ApplicationStatusResponse(UUID id, ApplicationStatus previousStatus, ApplicationStatus status,
		String reason, Instant updatedAt) {

	public static ApplicationStatusResponse from(Application application, ApplicationStatus previousStatus) {
		return new ApplicationStatusResponse(application.getId(), previousStatus, application.getStatus(),
				application.getStatusReason(), application.getUpdatedAt());
	}
}
