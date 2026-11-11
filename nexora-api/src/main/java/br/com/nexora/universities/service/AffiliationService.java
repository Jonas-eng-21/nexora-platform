package br.com.nexora.universities.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.students.entity.Student;
import br.com.nexora.students.service.StudentProfileService;
import br.com.nexora.universities.dto.AffiliationResponse;
import br.com.nexora.universities.entity.AffiliationStatus;
import br.com.nexora.universities.entity.University;
import br.com.nexora.universities.entity.UniversityAffiliation;
import br.com.nexora.universities.repository.UniversityAffiliationRepository;
import br.com.nexora.universities.repository.UniversityRepository;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.repository.UserRepository;

@Service
public class AffiliationService {

	private final UniversityAffiliationRepository affiliationRepository;
	private final UniversityRepository universityRepository;
	private final UserRepository userRepository;
	private final StudentProfileService studentProfileService;

	public AffiliationService(UniversityAffiliationRepository affiliationRepository,
			UniversityRepository universityRepository, UserRepository userRepository,
			StudentProfileService studentProfileService) {
		this.affiliationRepository = affiliationRepository;
		this.universityRepository = universityRepository;
		this.userRepository = userRepository;
		this.studentProfileService = studentProfileService;
	}

	/**
	 * Abre uma solicitação pendente para o estudante autenticado. As checagens abaixo dão mensagens claras;
	 * os índices únicos parciais do banco são a garantia final contra duas solicitações simultâneas.
	 */
	@Transactional
	public AffiliationResponse request(UUID studentUserId, UUID universityId) {
		Student student = studentProfileService.getByUserId(studentUserId);
		University university = universityRepository.findById(universityId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UNIVERSITY_NOT_FOUND",
						"Universidade não encontrada."));
		if (!isActive(university)) {
			throw new ApiException(HttpStatus.CONFLICT, "UNIVERSITY_NOT_ACTIVE",
					"Esta universidade não está ativa na plataforma.");
		}
		if (affiliationRepository.existsByStudentIdAndStatus(student.getId(), AffiliationStatus.APPROVED)) {
			throw new ApiException(HttpStatus.CONFLICT, "AFFILIATION_ALREADY_APPROVED",
					"Você já possui um vínculo acadêmico aprovado.");
		}
		if (affiliationRepository.existsByStudentIdAndStatus(student.getId(), AffiliationStatus.PENDING)) {
			throw new ApiException(HttpStatus.CONFLICT, "AFFILIATION_ALREADY_PENDING",
					"Você já possui uma solicitação de vínculo aguardando análise.");
		}
		return AffiliationResponse.from(affiliationRepository.save(new UniversityAffiliation(student, university)));
	}

	/** Histórico do estudante autenticado, do pedido mais recente para o mais antigo. */
	@Transactional(readOnly = true)
	public List<AffiliationResponse> listOwn(UUID studentUserId) {
		Student student = studentProfileService.getByUserId(studentUserId);
		return affiliationRepository.findByStudentIdOrderByRequestedAtDesc(student.getId()).stream()
				.map(AffiliationResponse::from).toList();
	}

	private boolean isActive(University university) {
		return userRepository.findById(university.getUserId())
				.map(account -> account.getStatus() == AccountStatus.ACTIVE)
				.orElse(false);
	}
}
