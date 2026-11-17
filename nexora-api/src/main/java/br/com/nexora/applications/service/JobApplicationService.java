package br.com.nexora.applications.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.applications.dto.JobApplicationResponse;
import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.repository.ApplicationRepository;
import br.com.nexora.jobs.entity.Job;
import br.com.nexora.jobs.service.JobService;
import br.com.nexora.matching.service.MatchingService;
import br.com.nexora.shared.dto.PageResponse;
import br.com.nexora.students.entity.Student;

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
		Page<Application> applications = applicationRepository.searchByJob(job.getId(), status, pageable);
		// Um cálculo por página (e, com o matching semântico ligado, os embeddings em lote), nunca um por linha.
		List<Student> students = applications.getContent().stream().map(Application::getStudent).toList();
		List<Optional<Integer>> scores = matchingService.currentScores(job, students);
		Map<UUID, Integer> scoreByApplication = new HashMap<>();
		for (int i = 0; i < students.size(); i++) {
			scoreByApplication.put(applications.getContent().get(i).getId(), scores.get(i).orElse(null));
		}
		return PageResponse.from(applications, application -> JobApplicationResponse.from(application,
				scoreByApplication.get(application.getId())));
	}
}
