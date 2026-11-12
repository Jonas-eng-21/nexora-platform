package br.com.nexora.jobs.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.companies.entity.Company;
import br.com.nexora.companies.service.CompanyProfileService;
import br.com.nexora.jobs.dto.CreateJobRequest;
import br.com.nexora.jobs.dto.JobResponse;
import br.com.nexora.jobs.entity.Job;
import br.com.nexora.jobs.entity.JobSkill;
import br.com.nexora.jobs.entity.JobStatus;
import br.com.nexora.jobs.entity.SkillRequirement;
import br.com.nexora.jobs.repository.JobRepository;
import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.skills.service.SkillService;

@Service
public class JobService {

	private final JobRepository jobRepository;
	private final CompanyProfileService companyProfileService;
	private final SkillService skillService;

	public JobService(JobRepository jobRepository, CompanyProfileService companyProfileService,
			SkillService skillService) {
		this.jobRepository = jobRepository;
		this.companyProfileService = companyProfileService;
		this.skillService = skillService;
	}

	/** A vaga pertence sempre à empresa da conta autenticada, que precisa ter perfil preenchido. */
	@Transactional
	public JobResponse create(UUID userId, CreateJobRequest request) {
		Company company = companyProfileService.getByUserId(userId);
		Job job = new Job(company, request.status() == null ? JobStatus.OPEN : request.status());
		apply(job, request);
		return JobResponse.from(jobRepository.save(job));
	}

	@Transactional(readOnly = true)
	public Page<JobResponse> listOwn(UUID userId, JobStatus status, Pageable pageable) {
		Company company = companyProfileService.getByUserId(userId);
		return jobRepository.findActiveByCompanyId(company.getId(), status, pageable).map(JobResponse::from);
	}

	@Transactional
	public JobResponse updateAsCompany(UUID userId, UUID jobId, CreateJobRequest request) {
		Job job = getOwnedActiveJob(userId, jobId);
		apply(job, request);
		if (request.status() != null) {
			job.changeStatus(request.status());
		}
		return JobResponse.from(job);
	}

	@Transactional
	public JobResponse closeAsCompany(UUID userId, UUID jobId) {
		Job job = getOwnedActiveJob(userId, jobId);
		job.changeStatus(JobStatus.CLOSED);
		return JobResponse.from(job);
	}

	@Transactional
	public JobResponse reopenAsCompany(UUID userId, UUID jobId) {
		Job job = getOwnedActiveJob(userId, jobId);
		job.changeStatus(JobStatus.OPEN);
		return JobResponse.from(job);
	}

	@Transactional
	public void deleteAsCompany(UUID userId, UUID jobId) {
		getOwnedActiveJob(userId, jobId).markDeleted();
	}

	/** Uso exclusivo de intervenções administrativas, que registram auditoria. */
	@Transactional
	public JobResponse updateById(UUID jobId, CreateJobRequest request) {
		Job job = getActiveJobById(jobId);
		apply(job, request);
		if (request.status() != null) {
			job.changeStatus(request.status());
		}
		return JobResponse.from(job);
	}

	@Transactional
	public JobResponse closeById(UUID jobId) {
		Job job = getActiveJobById(jobId);
		job.changeStatus(JobStatus.CLOSED);
		return JobResponse.from(job);
	}

	@Transactional
	public JobResponse reopenById(UUID jobId) {
		Job job = getActiveJobById(jobId);
		job.changeStatus(JobStatus.OPEN);
		return JobResponse.from(job);
	}

	@Transactional
	public void deleteById(UUID jobId) {
		getActiveJobById(jobId).markDeleted();
	}

	private Job getOwnedActiveJob(UUID userId, UUID jobId) {
		Company company = companyProfileService.getByUserId(userId);
		return jobRepository.findByIdAndCompanyIdAndDeletedAtIsNull(jobId, company.getId())
				.orElseThrow(JobService::notFound);
	}

	private Job getActiveJobById(UUID jobId) {
		return jobRepository.findByIdAndDeletedAtIsNull(jobId).orElseThrow(JobService::notFound);
	}

	private void apply(Job job, CreateJobRequest request) {
		job.updateDetails(request.title(), request.description(), request.area(), request.location(),
				request.workModel(), request.level());
		job.replaceSkills(resolveSkills(request.requiredSkillIds(), request.desiredSkillIds()));
	}

	private List<JobSkill> resolveSkills(Set<UUID> requiredIds, Set<UUID> desiredIds) {
		Set<UUID> desired = desiredIds == null ? Set.of() : desiredIds;
		if (!Collections.disjoint(requiredIds, desired)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "JOB_SKILL_CONFLICT",
					"Uma competência não pode ser obrigatória e desejável ao mesmo tempo.");
		}
		List<JobSkill> skills = new ArrayList<>();
		skillService.getAllByIds(requiredIds).forEach(skill -> skills.add(new JobSkill(skill, SkillRequirement.REQUIRED)));
		if (!desired.isEmpty()) {
			skillService.getAllByIds(desired).forEach(skill -> skills.add(new JobSkill(skill, SkillRequirement.DESIRED)));
		}
		return skills;
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Vaga não encontrada.");
	}
}
