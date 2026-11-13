package br.com.nexora.applications.repository;

import java.util.UUID;

/**
 * Quem está ligado a uma candidatura, lido sem carregar a entidade: para decidir a titularidade e tomar as travas na
 * ordem certa antes de bloquear (e só então ler) a linha da candidatura.
 */
public record ApplicationParties(UUID studentId, UUID studentUserId, UUID companyUserId, UUID jobId) {
}
