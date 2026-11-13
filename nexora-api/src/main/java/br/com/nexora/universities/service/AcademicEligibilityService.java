package br.com.nexora.universities.service;

import java.util.List;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.universities.entity.UniversityAffiliation;
import br.com.nexora.universities.repository.UniversityAffiliationRepository;
import br.com.nexora.users.repository.UserRepository;
import org.springframework.transaction.annotation.Propagation;

/**
 * Ponto único da regra de elegibilidade acadêmica: o estudante é elegível quando tem um vínculo APPROVED
 * (não revogado) com uma universidade cuja conta está ACTIVE. Candidaturas, suspensão e reativação devem
 * consultar este serviço, nunca repetir a regra.
 */
@Service
public class AcademicEligibilityService {

	private final UniversityAffiliationRepository affiliationRepository;
	private final UserRepository userRepository;

	public AcademicEligibilityService(UniversityAffiliationRepository affiliationRepository,
			UserRepository userRepository) {
		this.affiliationRepository = affiliationRepository;
		this.userRepository = userRepository;
	}

	/** @param studentId id do perfil de estudante (tabela students), não da conta. */
	@Transactional(readOnly = true)
	public boolean isEligible(UUID studentId) {
		return affiliationRepository.existsApprovedWithActiveUniversity(studentId);
	}

	/** Ids dos perfis de estudante hoje elegíveis, para listagens que só podem incluir estudantes validados. */
	@Transactional(readOnly = true)
	public List<UUID> eligibleStudentIds() {
		return affiliationRepository.findEligibleStudentIds();
	}

	/** Estudantes com vínculo aprovado com a universidade da conta informada (inclusive se ela estiver suspensa). */
	@Transactional(readOnly = true)
	public List<UUID> approvedStudentIdsOf(UUID universityUserId) {
		return affiliationRepository.findApprovedStudentIdsByUniversityUserId(universityUserId);
	}

	/** Para operações que exigem validação acadêmica: falha com 403 STUDENT_NOT_VALIDATED. */
	@Transactional(readOnly = true)
	public void requireEligible(UUID studentId) {
		if (!isEligible(studentId)) {
			throw notValidated();
		}
	}

	/**
	 * Como {@link #requireEligible}, mas prende o vínculo aprovado e a conta da universidade com trava
	 * compartilhada até o fim da transação de quem chama. Para operações que CRIAM algo que só pode existir enquanto
	 * o estudante é elegível (uma candidatura nova): a revogação e a suspensão da universidade passam a esperar essa
	 * transação terminar — e então enxergam a candidatura recém-criada —, ou terminam antes e a leitura aqui falha.
	 * A ordem das travas (vínculo, depois conta) é a mesma em todos os caminhos, para não haver deadlock.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void requireEligibleLocked(UUID studentId) {
		if (!lockEligibility(studentId)) {
			throw notValidated();
		}
	}

	/**
	 * Prende as mesmas travas de {@link #requireEligibleLocked}, mas só informa se o estudante é elegível, sem
	 * lançar erro. Para quem precisa tomar as travas antes de bloquear outra linha (a ordem global é vínculo, conta,
	 * candidaturas) e ainda decidir a precedência dos erros depois.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean lockEligibility(UUID studentId) {
		Optional<UniversityAffiliation> affiliation = affiliationRepository.findApprovedByStudentIdForShare(studentId);
		return affiliation.isPresent()
				&& userRepository.findActiveByIdForShare(affiliation.get().getUniversity().getUserId()).isPresent();
	}

	/** O erro padrão de estudante sem validação vigente (403 STUDENT_NOT_VALIDATED). */
	public static ApiException notValidated() {
		return new ApiException(HttpStatus.FORBIDDEN, "STUDENT_NOT_VALIDATED",
				"O estudante não possui vínculo acadêmico aprovado e vigente com uma universidade ativa.");
	}
}
