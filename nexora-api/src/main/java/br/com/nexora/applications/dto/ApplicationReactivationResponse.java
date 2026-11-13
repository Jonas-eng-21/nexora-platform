package br.com.nexora.applications.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.nexora.applications.entity.ApplicationStatus;

/** Resultado de uma reativação: nunca traz o currículo, o PDF nem dados da suspensão. */
public record ApplicationReactivationResponse(UUID id, ApplicationStatus previousStatus, ApplicationStatus status,
		Instant updatedAt) {
}
