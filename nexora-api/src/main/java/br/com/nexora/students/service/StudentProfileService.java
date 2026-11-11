package br.com.nexora.students.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.skills.entity.Skill;
import br.com.nexora.skills.service.SkillService;
import br.com.nexora.students.dto.UpdateStudentProfileRequest;
import br.com.nexora.students.entity.Student;
import br.com.nexora.students.repository.StudentRepository;
import br.com.nexora.students.resume.Resume;

@Service
public class StudentProfileService {

	private final StudentRepository studentRepository;
	private final SkillService skillService;

	public StudentProfileService(StudentRepository studentRepository, SkillService skillService) {
		this.studentRepository = studentRepository;
		this.skillService = skillService;
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

	/** Competências técnicas do currículo-base, em ordem alfabética. */
	@Transactional(readOnly = true)
	public List<Skill> getSkills(UUID userId) {
		return sortedByName(getByUserId(userId).getSkills());
	}

	/** Substitui a seleção de competências. Todas devem existir no catálogo. */
	@Transactional
	public List<Skill> replaceSkills(UUID userId, Set<UUID> skillIds) {
		Student student = getByUserId(userId);
		student.replaceSkills(skillService.getAllByIds(skillIds));
		return sortedByName(student.getSkills());
	}

	private static List<Skill> sortedByName(Collection<Skill> skills) {
		return skills.stream().sorted(Comparator.comparing(Skill::getNormalizedName)).toList();
	}

	/** Substitui o currículo-base inteiro. Exige perfil já criado. */
	@Transactional
	public Resume saveResume(UUID userId, Resume resume) {
		Student student = getByUserId(userId);
		student.replaceResume(resume);
		return student.getResume();
	}
}
