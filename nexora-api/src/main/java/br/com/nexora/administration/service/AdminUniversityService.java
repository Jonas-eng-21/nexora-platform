package br.com.nexora.administration.service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.administration.dto.AdminUniversityResponse;
import br.com.nexora.administration.entity.AdminAction;
import br.com.nexora.administration.entity.AuditTargetType;
import br.com.nexora.universities.entity.University;
import br.com.nexora.universities.entity.UniversityApprovalStatus;
import br.com.nexora.universities.service.UniversityApprovalService;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.repository.UserRepository;

@Service
public class AdminUniversityService {

	private final UniversityApprovalService universityApprovalService;
	private final UserRepository userRepository;
	private final AuditLogService auditLogService;

	public AdminUniversityService(UniversityApprovalService universityApprovalService, UserRepository userRepository,
			AuditLogService auditLogService) {
		this.universityApprovalService = universityApprovalService;
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional(readOnly = true)
	public Page<AdminUniversityResponse> list(UniversityApprovalStatus approvalStatus, Pageable pageable) {
		Page<University> universities = universityApprovalService.list(approvalStatus, pageable);
		// Uma consulta para todas as contas da página, em vez de uma por universidade.
		Map<UUID, User> accounts = userRepository
				.findAllById(universities.getContent().stream().map(University::getUserId).toList()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		return universities.map(university -> AdminUniversityResponse.from(university,
				accounts.get(university.getUserId())));
	}

	@Transactional(readOnly = true)
	public AdminUniversityResponse get(UUID universityId) {
		return toResponse(universityApprovalService.get(universityId));
	}

	/** Decisão e registro de auditoria na mesma transação. */
	@Transactional
	public AdminUniversityResponse approve(UUID adminId, UUID universityId, String reason) {
		University university = universityApprovalService.approve(universityId, adminId, reason);
		auditLogService.record(adminId, AdminAction.UNIVERSITY_APPROVED, AuditTargetType.UNIVERSITY, universityId,
				university.getApprovalReason());
		return toResponse(university);
	}

	@Transactional
	public AdminUniversityResponse reject(UUID adminId, UUID universityId, String reason) {
		University university = universityApprovalService.reject(universityId, adminId, reason);
		auditLogService.record(adminId, AdminAction.UNIVERSITY_REJECTED, AuditTargetType.UNIVERSITY, universityId,
				university.getApprovalReason());
		return toResponse(university);
	}

	private AdminUniversityResponse toResponse(University university) {
		User account = userRepository.findById(university.getUserId())
				.orElseThrow(() -> new IllegalStateException("University without account: " + university.getId()));
		return AdminUniversityResponse.from(university, account);
	}
}
