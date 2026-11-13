package br.com.nexora.applications.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.applications.dto.ApplicationStatusResponse;
import br.com.nexora.applications.dto.ChangeApplicationStatusRequest;
import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.repository.ApplicationRepository;
import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.universities.service.AcademicEligibilityService;

/**
 * Mudança do andamento de uma candidatura (SUBMITTED, IN_REVIEW, APPROVED, REJECTED). As regras ficam aqui,
 * num ponto só, para a empresa dona e para o admin. A suspensão (SUSPENDED) não é decidida por aqui.
 */
@Service
public class ApplicationStatusService {

	private final ApplicationRepository applicationRepository;
	private final AcademicEligibilityService eligibilityService;

	public ApplicationStatusService(ApplicationRepository applicationRepository,
			AcademicEligibilityService eligibilityService) {
		this.applicationRepository = applicationRepository;
		this.eligibilityService = eligibilityService;
	}

	/** Só a empresa dona da vaga; para qualquer outra, a candidatura "não existe" (404). */
	@Transactional
	public ApplicationStatusResponse changeAsCompany(UUID companyUserId, UUID applicationId,
			ChangeApplicationStatusRequest request) {
		Application application = lock(applicationId);
		if (!application.getJob().getCompany().getUserId().equals(companyUserId)) {
			throw notFound();
		}
		return change(application, request);
	}

	/** Uso exclusivo da rota do administrador, que registra a auditoria na mesma transação. */
	@Transactional
	public ApplicationStatusResponse changeById(UUID applicationId, ChangeApplicationStatusRequest request) {
		return change(lock(applicationId), request);
	}

	/**
	 * Ordem das checagens: suspensa → transição permitida → motivo da rejeição → elegibilidade acadêmica atual
	 * (regra única de {@link AcademicEligibilityService}). Nada é alterado se qualquer uma falhar.
	 */
	private ApplicationStatusResponse change(Application application, ChangeApplicationStatusRequest request) {
		ApplicationStatus from = application.getStatus();
		ApplicationStatus to = request.status();
		if (from == ApplicationStatus.SUSPENDED) {
			throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_SUSPENDED",
					"Esta candidatura está suspensa e não pode avançar até ser reativada.");
		}
		if (!from.canMoveTo(to)) {
			throw new ApiException(HttpStatus.CONFLICT, "INVALID_APPLICATION_STATUS_TRANSITION",
					"Não é possível mudar o andamento de " + from + " para " + to + ".");
		}
		String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
		if (to == ApplicationStatus.REJECTED && reason == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED",
					"Informe o motivo da rejeição.");
		}
		eligibilityService.requireEligible(application.getStudent().getId());

		application.changeStatus(to, reason);
		// Grava agora para a resposta já trazer o updatedAt novo.
		applicationRepository.saveAndFlush(application);
		return ApplicationStatusResponse.from(application, from);
	}

	private Application lock(UUID applicationId) {
		return applicationRepository.findByIdForUpdate(applicationId).orElseThrow(ApplicationStatusService::notFound);
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Candidatura não encontrada.");
	}
}
