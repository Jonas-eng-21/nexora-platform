package br.com.nexora.applications.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.applications.entity.SuspensionReason;
import br.com.nexora.applications.repository.ApplicationRepository;

/**
 * Suspende as candidaturas em andamento (SUBMITTED e IN_REVIEW) de estudantes que perderam a elegibilidade. É uma
 * atualização em lote única: guarda o estado anterior, o motivo e o instante, e não toca em candidaturas finais
 * nem nas que já estão suspensas (que mantêm os dados da primeira suspensão), então repetir é inofensivo.
 */
@Service
public class ApplicationSuspensionService {

	private final ApplicationRepository applicationRepository;

	public ApplicationSuspensionService(ApplicationRepository applicationRepository) {
		this.applicationRepository = applicationRepository;
	}

	/**
	 * Exige a transação de quem a disparou (MANDATORY): a suspensão só vale se acontecer junto com a revogação ou
	 * a suspensão da universidade, e falhar aqui desfaz tudo. Sem transação, falha em vez de seguir pela metade.
	 *
	 * @return quantas candidaturas foram suspensas
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int suspendInProgress(Collection<UUID> studentIds, SuspensionReason reason) {
		if (studentIds.isEmpty()) {
			return 0;
		}
		return applicationRepository.suspendInProgress(studentIds, reason,
				Instant.now().truncatedTo(ChronoUnit.MICROS));
	}
}
