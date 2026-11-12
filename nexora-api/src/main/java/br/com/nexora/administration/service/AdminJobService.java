package br.com.nexora.administration.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.administration.dto.AdminUpdateJobRequest;
import br.com.nexora.administration.entity.AdminAction;
import br.com.nexora.administration.entity.AuditTargetType;
import br.com.nexora.jobs.dto.JobResponse;
import br.com.nexora.jobs.service.JobService;

@Service
public class AdminJobService {

	private final JobService jobService;
	private final AuditLogService auditLogService;

	public AdminJobService(JobService jobService, AuditLogService auditLogService) {
		this.jobService = jobService;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public JobResponse update(UUID adminId, UUID jobId, AdminUpdateJobRequest request) {
		JobResponse response = jobService.updateById(jobId, request.job());
		auditLogService.record(adminId, AdminAction.JOB_UPDATED, AuditTargetType.JOB, jobId, request.reason());
		return response;
	}

	@Transactional
	public JobResponse close(UUID adminId, UUID jobId, String reason) {
		JobResponse response = jobService.closeById(jobId);
		auditLogService.record(adminId, AdminAction.JOB_CLOSED, AuditTargetType.JOB, jobId, reason);
		return response;
	}

	@Transactional
	public JobResponse reopen(UUID adminId, UUID jobId, String reason) {
		JobResponse response = jobService.reopenById(jobId);
		auditLogService.record(adminId, AdminAction.JOB_REOPENED, AuditTargetType.JOB, jobId, reason);
		return response;
	}

	@Transactional
	public void delete(UUID adminId, UUID jobId, String reason) {
		jobService.deleteById(jobId);
		auditLogService.record(adminId, AdminAction.JOB_DELETED, AuditTargetType.JOB, jobId, reason);
	}
}

