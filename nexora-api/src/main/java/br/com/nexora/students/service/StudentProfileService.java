package br.com.nexora.students.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.students.dto.UpdateStudentProfileRequest;
import br.com.nexora.students.entity.Student;
import br.com.nexora.students.repository.StudentRepository;

@Service
public class StudentProfileService {

	private final StudentRepository studentRepository;

	public StudentProfileService(StudentRepository studentRepository) {
		this.studentRepository = studentRepository;
	}

	@Transactional(readOnly = true)
	public Student getByUserId(UUID userId) {
		return studentRepository.findByUserId(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STUDENT_PROFILE_NOT_FOUND",
						"Perfil de estudante ainda não preenchido."));
	}

	/** Cria o perfil no primeiro envio e o atualiza nos seguintes. O dono é sempre o usuário autenticado. */
	@Transactional
	public Student saveProfile(UUID userId, UpdateStudentProfileRequest request) {
		Student student = studentRepository.findByUserId(userId).orElseGet(() -> new Student(userId));
		student.updateProfile(request.fullName(), request.course(), request.institution(), request.location(),
				request.summary());
		return studentRepository.save(student);
	}
}
