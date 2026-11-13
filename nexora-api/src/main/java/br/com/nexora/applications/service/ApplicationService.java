package br.com.nexora.applications.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.applications.dto.ApplicationResponse;
import br.com.nexora.applications.dto.CreateApplicationRequest;
import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.repository.ApplicationRepository;
import br.com.nexora.jobs.entity.Job;
import br.com.nexora.jobs.service.JobService;
import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.students.entity.Student;
import br.com.nexora.students.service.StudentProfileService;
import br.com.nexora.universities.service.AcademicEligibilityService;

@Service
public class ApplicationService {

	private final ApplicationRepository applicationRepository;
	private final StudentProfileService studentProfileService;
	private final AcademicEligibilityService eligibilityService;
	private final JobService jobService;

	public ApplicationService(ApplicationRepository applicationRepository,
			StudentProfileService studentProfileService, AcademicEligibilityService eligibilityService,
			JobService jobService) {
		this.applicationRepository = applicationRepository;
		this.studentProfileService = studentProfileService;
		this.eligibilityService = eligibilityService;
		this.jobService = jobService;
	}

	/**
	 * Candidatura do estudante autenticado. As regras de elegibilidade e de vaga aberta vêm dos pontos únicos
	 * ({@link AcademicEligibilityService} e {@link JobService}); a duplicidade é barrada aqui e, sob concorrência,
	 * pela unicidade (estudante, vaga) do banco.
	 */
	@Transactional
	public ApplicationResponse apply(UUID studentUserId, UUID jobId, CreateApplicationRequest request) {
		Student student = studentProfileService.getByUserId(studentUserId);
		eligibilityService.requireEligible(student.getId());
		Job job = jobService.requireAcceptingApplications(jobId);
		if (applicationRepository.existsByStudentIdAndJobId(student.getId(), job.getId())) {
			throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS",
					"Você já se candidatou a esta vaga.");
		}
		// Só há um tipo de documento por enquanto; o PDF terá o próprio caminho (US-28).
		return ApplicationResponse.from(applicationRepository.saveAndFlush(Application.withPlatformResume(student, job)));
	}
}
