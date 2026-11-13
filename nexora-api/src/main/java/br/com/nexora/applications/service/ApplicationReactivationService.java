package br.com.nexora.applications.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.applications.dto.ApplicationReactivationResponse;
import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.repository.ApplicationParties;
import br.com.nexora.applications.repository.ApplicationRepository;
import br.com.nexora.jobs.service.JobService;
import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.universities.service.AcademicEligibilityService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.service.UserAccountService;

/**
 * Reativação explícita de uma candidatura SUSPENDED, pela empresa dona ou pelo admin: restaura exatamente o estado
 * guardado na suspensão. Nunca é automática. Um só serviço para os dois chamadores.
 */
@Service
public class ApplicationReactivationService {

	private final ApplicationRepository applicationRepository;
	private final AcademicEligibilityService eligibilityService;
	private final JobService jobService;
	private final UserAccountService userAccountService;

	public ApplicationReactivationService(ApplicationRepository applicationRepository,
			AcademicEligibilityService eligibilityService, JobService jobService,
			UserAccountService userAccountService) {
		this.applicationRepository = applicationRepository;
		this.eligibilityService = eligibilityService;
		this.jobService = jobService;
		this.userAccountService = userAccountService;
	}

	/** Só a empresa dona da vaga; para qualquer outra, a candidatura "não existe" (404). */
	@Transactional
	public ApplicationReactivationResponse reactivateAsCompany(UUID companyUserId, UUID applicationId) {
		ApplicationParties parties = parties(applicationId);
		if (!parties.companyUserId().equals(companyUserId)) {
			throw notFound();
		}
		return reactivate(parties, applicationId);
	}

	/** Uso exclusivo da rota do administrador, que registra a auditoria na mesma transação. */
	@Transactional
	public ApplicationReactivationResponse reactivateById(UUID applicationId) {
		return reactivate(parties(applicationId), applicationId);
	}

	/**
	 * Ordem das travas, a mesma de toda a US-32: vínculo e conta da universidade (compartilhadas), depois a linha
	 * da candidatura (exclusiva). Assim, uma revogação ou suspensão em andamento vence a reativação (que então falha
	 * com 403) e uma reativação concluída é vista — e suspensa de novo — pela revogação que vier depois. A leitura
	 * da candidatura só acontece depois das travas, para enxergar o estado já confirmado por quem a precedeu.
	 * <p>
	 * Ordem das checagens: suspensa → estado guardado válido → conta do estudante → elegibilidade acadêmica →
	 * vaga disponível. Nada é alterado se qualquer uma falhar.
	 */
	private ApplicationReactivationResponse reactivate(ApplicationParties parties, UUID applicationId) {
		boolean eligible = eligibilityService.lockEligibility(parties.studentId());
		Application application = applicationRepository.findByIdForUpdate(applicationId)
				.orElseThrow(ApplicationReactivationService::notFound);

		if (application.getStatus() != ApplicationStatus.SUSPENDED) {
			throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_NOT_SUSPENDED",
					"Apenas candidaturas suspensas podem ser reativadas.");
		}
		ApplicationStatus restored = application.getSuspendedFromStatus();
		if (restored == null || !restored.isInProgress()) {
			// Não deveria existir (a V18 impede), mas nunca se escolhe um estado por padrão ou por inferência.
			throw new ApiException(HttpStatus.CONFLICT, "INVALID_SUSPENSION_STATE",
					"O estado anterior à suspensão está ausente ou é inválido; a candidatura não foi alterada.");
		}
		if (userAccountService.getAccount(parties.studentUserId()).getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.CONFLICT, "STUDENT_ACCOUNT_NOT_ACTIVE",
					"A conta do estudante não está ativa.");
		}
		if (!eligible) {
			throw AcademicEligibilityService.notValidated();
		}
		if (!jobService.isAcceptingApplications(parties.jobId())) {
			throw new ApiException(HttpStatus.CONFLICT, "JOB_NOT_AVAILABLE",
					"A vaga não está disponível para continuar o processo seletivo.");
		}

		application.reactivate();
		// Grava agora para a resposta já trazer o updatedAt novo.
		applicationRepository.saveAndFlush(application);
		return new ApplicationReactivationResponse(application.getId(), ApplicationStatus.SUSPENDED,
				application.getStatus(), application.getUpdatedAt());
	}

	private ApplicationParties parties(UUID applicationId) {
		return applicationRepository.findParties(applicationId).orElseThrow(ApplicationReactivationService::notFound);
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Candidatura não encontrada.");
	}
}
