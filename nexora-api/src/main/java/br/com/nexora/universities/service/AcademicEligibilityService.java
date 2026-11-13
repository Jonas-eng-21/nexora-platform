package br.com.nexora.universities.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.universities.repository.UniversityAffiliationRepository;

/**
 * Ponto único da regra de elegibilidade acadêmica: o estudante é elegível quando tem um vínculo APPROVED
 * (não revogado) com uma universidade cuja conta está ACTIVE. Candidaturas, suspensão e reativação devem
 * consultar este serviço, nunca repetir a regra.
 */
@Service
public class AcademicEligibilityService {

	private final UniversityAffiliationRepository affiliationRepository;

	public AcademicEligibilityService(UniversityAffiliationRepository affiliationRepository) {
		this.affiliationRepository = affiliationRepository;
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

	/** Para operações que exigem validação acadêmica: falha com 403 STUDENT_NOT_VALIDATED. */
	@Transactional(readOnly = true)
	public void requireEligible(UUID studentId) {
		if (!isEligible(studentId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "STUDENT_NOT_VALIDATED",
					"O estudante não possui vínculo acadêmico aprovado e vigente com uma universidade ativa.");
		}
	}
}
