package br.com.nexora.universities.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record RequestAffiliationRequest(@NotNull UUID universityId) {
}
