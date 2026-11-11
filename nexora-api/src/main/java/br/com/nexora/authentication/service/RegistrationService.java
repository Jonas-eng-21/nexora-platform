package br.com.nexora.authentication.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.authentication.dto.RegisterRequest;
import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.universities.service.UniversityProfileService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import br.com.nexora.users.service.PasswordPolicy;

@Service
public class RegistrationService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final UniversityProfileService universityProfileService;

	public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			UniversityProfileService universityProfileService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.universityProfileService = universityProfileService;
	}

	@Transactional
	public User register(RegisterRequest request) {
		if (request.role() == UserRole.ADMIN) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_ALLOWED",
					"Este perfil não pode ser criado pelo cadastro público.");
		}
		requireConsistentUniversityData(request);
		PasswordPolicy.requireHashable(request.password());
		if (userRepository.existsByEmail(User.normalizeEmail(request.email()))) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "E-mail já cadastrado.");
		}
		User user = userRepository.save(new User(request.email(), passwordEncoder.encode(request.password()),
				request.role(), initialStatus(request.role())));
		if (request.role() == UserRole.UNIVERSITY) {
			universityProfileService.createForNewAccount(user.getId(), request.university());
		}
		return user;
	}

	private static void requireConsistentUniversityData(RegisterRequest request) {
		boolean isUniversity = request.role() == UserRole.UNIVERSITY;
		if (isUniversity && request.university() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "UNIVERSITY_DATA_REQUIRED",
					"Informe os dados institucionais da universidade.");
		}
		if (!isUniversity && request.university() != null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "UNIVERSITY_DATA_NOT_ALLOWED",
					"Dados institucionais só são aceitos no cadastro de universidade.");
		}
	}

	/** Universidades só operam após aprovação do administrador; o status nunca vem do cliente. */
	private static AccountStatus initialStatus(UserRole role) {
		return role == UserRole.UNIVERSITY ? AccountStatus.PENDING_APPROVAL : AccountStatus.ACTIVE;
	}
}
