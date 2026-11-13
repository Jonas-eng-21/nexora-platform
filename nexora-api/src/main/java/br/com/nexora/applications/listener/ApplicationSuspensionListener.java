package br.com.nexora.applications.listener;

import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import br.com.nexora.applications.entity.SuspensionReason;
import br.com.nexora.applications.service.ApplicationSuspensionService;
import br.com.nexora.universities.event.AcademicAffiliationRevoked;
import br.com.nexora.universities.event.UniversitySuspended;
import br.com.nexora.universities.service.AcademicEligibilityService;

/**
 * Reage aos eventos de {@code universities}. Usa {@code @EventListener} comum, que o Spring entrega de forma
 * síncrona, na mesma thread e dentro da transação de quem publicou: uma falha aqui desfaz a revogação ou a
 * suspensão. Não trocar por {@code @Async} nem por {@code @TransactionalEventListener(AFTER_COMMIT)}.
 */
@Component
public class ApplicationSuspensionListener {

	private final ApplicationSuspensionService suspensionService;
	private final AcademicEligibilityService eligibilityService;

	public ApplicationSuspensionListener(ApplicationSuspensionService suspensionService,
			AcademicEligibilityService eligibilityService) {
		this.suspensionService = suspensionService;
		this.eligibilityService = eligibilityService;
	}

	@EventListener
	public void onAffiliationRevoked(AcademicAffiliationRevoked event) {
		suspensionService.suspendInProgress(Set.of(event.studentId()), SuspensionReason.ACADEMIC_AFFILIATION_REVOKED);
	}

	@EventListener
	public void onUniversitySuspended(UniversitySuspended event) {
		suspensionService.suspendInProgress(eligibilityService.approvedStudentIdsOf(event.universityUserId()),
				SuspensionReason.UNIVERSITY_SUSPENDED);
	}
}
