package br.com.nexora.administration.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.administration.entity.AdminAction;
import br.com.nexora.administration.entity.AuditTargetType;
import br.com.nexora.universities.dto.ReceivedAffiliationResponse;
import br.com.nexora.universities.entity.AffiliationStatus;
import br.com.nexora.universities.service.AffiliationDecisionService;

@Service
public class AdminAffiliationService {

	private final AffiliationDecisionService affiliationDecisionService;
	private final AuditLogService auditLogService;

	public AdminAffiliationService(AffiliationDecisionService affiliationDecisionService,
			AuditLogService auditLogService) {
		this.affiliationDecisionService = affiliationDecisionService;
		this.auditLogService = auditLogService;
	}

	/** Decisão e registro de auditoria na mesma transação. */
	@Transactional
	public ReceivedAffiliationResponse approve(UUID adminId, UUID affiliationId, String reason) {
		ReceivedAffiliationResponse response = affiliationDecisionService.decideAsAdmin(adminId, affiliationId,
				AffiliationStatus.APPROVED, reason);
		auditLogService.record(adminId, AdminAction.AFFILIATION_APPROVED, AuditTargetType.AFFILIATION, affiliationId,
				reason);
		return response;
	}

	@Transactional
	public ReceivedAffiliationResponse reject(UUID adminId, UUID affiliationId, String reason) {
		ReceivedAffiliationResponse response = affiliationDecisionService.decideAsAdmin(adminId, affiliationId,
				AffiliationStatus.REJECTED, reason);
		auditLogService.record(adminId, AdminAction.AFFILIATION_REJECTED, AuditTargetType.AFFILIATION, affiliationId,
				reason);
		return response;
	}
}
