package br.com.nexora.students.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.shared.security.AuthenticatedUser;
import br.com.nexora.skills.dto.SkillResponse;
import br.com.nexora.students.dto.UpdateStudentSkillsRequest;
import br.com.nexora.students.dto.StudentProfileResponse;
import br.com.nexora.students.dto.UpdateStudentProfileRequest;
import br.com.nexora.students.resume.Resume;
import br.com.nexora.students.service.StudentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/students/me")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Estudante")
public class StudentProfileController {

	private final StudentProfileService studentProfileService;

	public StudentProfileController(StudentProfileService studentProfileService) {
		this.studentProfileService = studentProfileService;
	}

	@GetMapping
	@Operation(summary = "Consultar o próprio perfil de estudante",
			description = "404 STUDENT_PROFILE_NOT_FOUND enquanto o perfil não for preenchido.")
	public StudentProfileResponse getProfile(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
		return StudentProfileResponse.from(studentProfileService.getByUserId(principal.id()));
	}

	@PutMapping
	@Operation(summary = "Criar ou atualizar o próprio perfil de estudante",
			description = "Substituição integral: campos opcionais ausentes são limpos.")
	public StudentProfileResponse saveProfile(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody UpdateStudentProfileRequest request) {
		return StudentProfileResponse.from(studentProfileService.saveProfile(principal.id(), request));
	}

	@GetMapping("/skills")
	@Operation(summary = "Consultar as próprias competências técnicas")
	public List<SkillResponse> getSkills(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
		return studentProfileService.getSkills(principal.id()).stream().map(SkillResponse::from).toList();
	}

	@PutMapping("/skills")
	@Operation(summary = "Definir as próprias competências técnicas a partir do catálogo",
			description = "Substitui a seleção anterior. Competência fora do catálogo: 400 SKILL_NOT_FOUND.")
	public List<SkillResponse> replaceSkills(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody UpdateStudentSkillsRequest request) {
		return studentProfileService.replaceSkills(principal.id(), request.skillIds()).stream()
				.map(SkillResponse::from).toList();
	}

	@GetMapping("/resume")
	@Operation(summary = "Consultar o próprio currículo-base",
			description = "Seções ainda não preenchidas vêm como listas vazias.")
	public Resume getResume(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
		return studentProfileService.getByUserId(principal.id()).getResume();
	}

	@PutMapping("/resume")
	@Operation(summary = "Salvar o próprio currículo-base",
			description = "Substituição integral do documento. É a única fonte de dados do matching; "
					+ "um PDF enviado numa candidatura não o substitui.")
	public Resume saveResume(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody Resume resume) {
		return studentProfileService.saveResume(principal.id(), resume);
	}
}
