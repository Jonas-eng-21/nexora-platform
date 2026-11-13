package br.com.nexora.applications.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.applications.dto.JobApplicationResponse;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.repository.ApplicationRepository;
import br.com.nexora.jobs.entity.Job;
import br.com.nexora.jobs.service.JobService;
import br.com.nexora.matching.service.MatchingService;
import br.com.nexora.shared.dto.PageResponse;

/**
 * Candidaturas recebidas por uma vaga, para a empresa dona (por propriedade) e para o admin (leitura). A
 * compatibilidade é calculada na hora por {@link MatchingService}, sem gravar nada e sem consultas por linha.
 */
@Service
public class JobApplicationService {

	private final ApplicationRepository applicationRepository;
	private final JobService jobService;
	private final MatchingService matchingService;

	public JobApplicationService(ApplicationRepository applicationRepository, JobService jobService,
			MatchingService matchingService) {
		this.applicationRepository = applicationRepository;
		this.jobService = jobService;
		this.matchingService = matchingService;
	}

	/** Vaga da empresa autenticada; de outra empresa, excluída ou inexistente responde 404 JOB_NOT_FOUND. */
	@Transactional(readOnly = true)
	public PageResponse<JobApplicationResponse> listForOwnJob(UUID companyUserId, UUID jobId,
			ApplicationStatus status, int page, int size) {
		return list(jobService.getOwnedActiveJob(companyUserId, jobId), status, page, size);
	}

	/** Qualquer vaga não excluída: rota de leitura do administrador. */
	@Transactional(readOnly = true)
	public PageResponse<JobApplicationResponse> listForJob(UUID jobId, ApplicationStatus status, int page,
			int size) {
		return list(jobService.getActiveJobById(jobId), status, page, size);
	}

	/** Todos os status (inclusive suspensas e rejeitadas), da mais recente para a mais antiga. */
	private PageResponse<JobApplicationResponse> list(Job job, ApplicationStatus status, int page, int size) {
		Pageable pageable = PageResponse.pageable(page, size, ApplicationService.MAX_LIST_SIZE,
				ApplicationService.NEWEST_FIRST);
		return PageResponse.from(applicationRepository.searchByJob(job.getId(), status, pageable),
				application -> JobApplicationResponse.from(application,
						matchingService.currentScore(application.getStudent(), job).orElse(null)));
	}
}
