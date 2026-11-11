package br.com.nexora.administration.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.administration.dto.AdminUpdateCompanyRequest;
import br.com.nexora.administration.entity.AdminAction;
import br.com.nexora.administration.entity.AuditTargetType;
import br.com.nexora.companies.entity.Company;
import br.com.nexora.companies.service.CompanyProfileService;

@Service
public class AdminCompanyService {

	private final CompanyProfileService companyProfileService;
	private final AuditLogService auditLogService;

	public AdminCompanyService(CompanyProfileService companyProfileService, AuditLogService auditLogService) {
		this.companyProfileService = companyProfileService;
		this.auditLogService = auditLogService;
	}

	/** Alteração e registro de auditoria na mesma transação. */
	@Transactional
	public Company updateProfile(UUID adminId, UUID companyId, AdminUpdateCompanyRequest request) {
		Company company = companyProfileService.updateById(companyId, request.profile());
		auditLogService.record(adminId, AdminAction.COMPANY_PROFILE_UPDATED, AuditTargetType.COMPANY, companyId,
				request.reason());
		return company;
	}
}
