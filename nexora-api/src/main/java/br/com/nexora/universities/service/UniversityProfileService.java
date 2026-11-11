package br.com.nexora.universities.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.universities.dto.UniversityProfileRequest;
import br.com.nexora.universities.entity.University;
import br.com.nexora.universities.repository.UniversityRepository;

@Service
public class UniversityProfileService {

	private final UniversityRepository universityRepository;

	public UniversityProfileService(UniversityRepository universityRepository) {
		this.universityRepository = universityRepository;
	}

	/**
	 * Cria os dados institucionais junto com a conta, na transação do cadastro: a universidade pendente
	 * não consegue autenticar, então é assim que o administrador recebe o que precisa para decidir.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public University createForNewAccount(UUID userId, UniversityProfileRequest request) {
		University university = new University(userId);
		apply(university, request);
		return universityRepository.save(university);
	}

	@Transactional(readOnly = true)
	public University getByUserId(UUID userId) {
		return universityRepository.findByUserId(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UNIVERSITY_PROFILE_NOT_FOUND",
						"Perfil de universidade não encontrado."));
	}

	/** A dona é sempre a conta autenticada. */
	@Transactional
	public University updateOwnProfile(UUID userId, UniversityProfileRequest request) {
		University university = getByUserId(userId);
		apply(university, request);
		return university;
	}

	private static void apply(University university, UniversityProfileRequest request) {
		university.updateProfile(request.name(), request.acronym(), request.location(), request.website(),
				request.description());
	}
}
