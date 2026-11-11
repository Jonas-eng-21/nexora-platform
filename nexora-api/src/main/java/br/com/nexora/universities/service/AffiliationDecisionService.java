package br.com.nexora.universities.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.universities.dto.ReceivedAffiliationResponse;
import br.com.nexora.universities.entity.AffiliationStatus;
import br.com.nexora.universities.entity.UniversityAffiliation;
import br.com.nexora.universities.repository.UniversityAffiliationRepository;

/**
 * Análise dos pedidos de vínculo. Ponto único da regra de titularidade: uma universidade só enxerga e decide
 * pedidos endereçados a ela. O administrador decide qualquer pedido pelo módulo de administração, que audita.
 */
@Service
public class AffiliationDecisionService {

	private final UniversityAffiliationRepository affiliationRepository;
	private final UniversityProfileService universityProfileService;

	public AffiliationDecisionService(UniversityAffiliationRepository affiliationRepository,
			UniversityProfileService universityProfileService) {
		this.affiliationRepository = affiliationRepository;
		this.universityProfileService = universityProfileService;
	}

	@Transactional(readOnly = true)
	public Page<ReceivedAffiliationResponse> listReceived(UUID universityUserId, AffiliationStatus status,
			Pageable pageable) {
		UUID universityId = universityProfileService.getByUserId(universityUserId).getId();
		return affiliationRepository.searchByUniversity(universityId, status, pageable)
				.map(ReceivedAffiliationResponse::from);
	}

	@Transactional(readOnly = true)
	public Page<ReceivedAffiliationResponse> listAll(AffiliationStatus status, Pageable pageable) {
		return affiliationRepository.searchAll(status, pageable).map(ReceivedAffiliationResponse::from);
	}

	/** Decisão da universidade destinatária. Pedido de outra instituição responde como inexistente. */
	@Transactional
	public ReceivedAffiliationResponse decideAsUniversity(UUID universityUserId, UUID affiliationId,
			AffiliationStatus decision, String reason) {
		UniversityAffiliation affiliation = lock(affiliationId);
		if (!affiliation.getUniversity().getUserId().equals(universityUserId)) {
			throw notFound();
		}
		return decide(affiliation, decision, universityUserId, reason);
	}

	/** Decisão do administrador sobre qualquer pedido. A auditoria é responsabilidade de quem chama. */
	@Transactional
	public ReceivedAffiliationResponse decideAsAdmin(UUID adminId, UUID affiliationId, AffiliationStatus decision,
			String reason) {
		return decide(lock(affiliationId), decision, adminId, reason);
	}

	private ReceivedAffiliationResponse decide(UniversityAffiliation affiliation, AffiliationStatus decision,
			UUID decidedBy, String reason) {
		if (decision != AffiliationStatus.APPROVED && decision != AffiliationStatus.REJECTED) {
			throw new IllegalArgumentException("Not a decision: " + decision);
		}
		if (affiliation.getStatus() != AffiliationStatus.PENDING) {
			throw new ApiException(HttpStatus.CONFLICT, "AFFILIATION_ALREADY_DECIDED",
					"Esta solicitação de vínculo já foi analisada.");
		}
		affiliation.recordDecision(decision, decidedBy, reason);
		return ReceivedAffiliationResponse.from(affiliation);
	}

	private UniversityAffiliation lock(UUID affiliationId) {
		return affiliationRepository.findByIdForUpdate(affiliationId).orElseThrow(AffiliationDecisionService::notFound);
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "AFFILIATION_NOT_FOUND",
				"Solicitação de vínculo não encontrada.");
	}
}
