package br.com.nexora.administration.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.administration.entity.AdminAction;
import br.com.nexora.administration.entity.AuditTargetType;
import br.com.nexora.applications.dto.ApplicationStatusResponse;
import br.com.nexora.applications.dto.ChangeApplicationStatusRequest;
import br.com.nexora.applications.service.ApplicationStatusService;

@Service
public class AdminApplicationService {

	private final ApplicationStatusService applicationStatusService;
	private final AuditLogService auditLogService;

	public AdminApplicationService(ApplicationStatusService applicationStatusService,
			AuditLogService auditLogService) {
		this.applicationStatusService = applicationStatusService;
		this.auditLogService = auditLogService;
	}

	/**
	 * Mesmas transições e regras da empresa. A mudança e o registro de auditoria (com status anterior e novo)
	 * acontecem na mesma transação: se a auditoria falhar, a mudança é desfeita.
	 */
	@Transactional
	public ApplicationStatusResponse changeStatus(UUID adminId, UUID applicationId,
			ChangeApplicationStatusRequest request) {
		ApplicationStatusResponse response = applicationStatusService.changeById(applicationId, request);
		auditLogService.record(adminId, AdminAction.APPLICATION_STATUS_CHANGED, AuditTargetType.APPLICATION,
				applicationId, response.reason(), response.previousStatus().name(), response.status().name());
		return response;
	}
}
