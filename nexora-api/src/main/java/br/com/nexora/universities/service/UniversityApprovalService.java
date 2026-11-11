package br.com.nexora.universities.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.universities.entity.University;
import br.com.nexora.universities.entity.UniversityApprovalStatus;
import br.com.nexora.universities.repository.UniversityRepository;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.repository.UserRepository;

/** Análise do cadastro de universidades. Quem pode decidir (ADMIN) e a auditoria ficam no módulo de administração. */
@Service
public class UniversityApprovalService {

	private final UniversityRepository universityRepository;
	private final UserRepository userRepository;

	public UniversityApprovalService(UniversityRepository universityRepository, UserRepository userRepository) {
		this.universityRepository = universityRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public Page<University> list(UniversityApprovalStatus approvalStatus, Pageable pageable) {
		return universityRepository.search(approvalStatus, pageable);
	}

	@Transactional(readOnly = true)
	public University get(UUID universityId) {
		return universityRepository.findById(universityId).orElseThrow(UniversityApprovalService::notFound);
	}

	/** Aprovação: a conta passa a ACTIVE e a universidade pode atuar como validadora. */
	@Transactional
	public University approve(UUID universityId, UUID adminId, String reason) {
		return decide(universityId, adminId, reason, UniversityApprovalStatus.APPROVED, AccountStatus.ACTIVE);
	}

	/** Rejeição: a conta passa a REJECTED e nunca recebe poder de aprovação. */
	@Transactional
	public University reject(UUID universityId, UUID adminId, String reason) {
		return decide(universityId, adminId, reason, UniversityApprovalStatus.REJECTED, AccountStatus.REJECTED);
	}

	private University decide(UUID universityId, UUID adminId, String reason, UniversityApprovalStatus decision,
			AccountStatus accountStatus) {
		University university = universityRepository.findByIdForUpdate(universityId)
				.orElseThrow(UniversityApprovalService::notFound);
		if (university.getApprovalStatus() != UniversityApprovalStatus.PENDING) {
			throw new ApiException(HttpStatus.CONFLICT, "UNIVERSITY_ALREADY_DECIDED",
					"O cadastro desta universidade já foi analisado.");
		}
		User account = userRepository.findById(university.getUserId())
				.orElseThrow(() -> new IllegalStateException("University without account: " + universityId));
		university.recordApprovalDecision(decision, adminId, reason);
		account.changeStatus(accountStatus);
		return university;
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "UNIVERSITY_NOT_FOUND", "Universidade não encontrada.");
	}
}
