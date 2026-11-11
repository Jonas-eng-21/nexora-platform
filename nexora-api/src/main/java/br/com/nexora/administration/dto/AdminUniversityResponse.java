package br.com.nexora.administration.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.nexora.universities.entity.University;
import br.com.nexora.universities.entity.UniversityApprovalStatus;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;

/** Visão do administrador: dados institucionais, conta e análise de aprovação. */
public record AdminUniversityResponse(UUID id, UUID userId, String email, AccountStatus accountStatus, String name,
		String acronym, String location, String website, String description, UniversityApprovalStatus approvalStatus,
		String approvalReason, UUID approvalDecidedBy, Instant approvalDecidedAt, Instant createdAt) {

	public static AdminUniversityResponse from(University university, User account) {
		return new AdminUniversityResponse(university.getId(), account.getId(), account.getEmail(),
				account.getStatus(), university.getName(), university.getAcronym(), university.getLocation(),
				university.getWebsite(), university.getDescription(), university.getApprovalStatus(),
				university.getApprovalReason(), university.getApprovalDecidedBy(), university.getApprovalDecidedAt(),
				university.getCreatedAt());
	}
}
