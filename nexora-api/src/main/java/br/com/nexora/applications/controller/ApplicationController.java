package br.com.nexora.applications.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.applications.dto.ApplicationResponse;
import br.com.nexora.applications.dto.CreateApplicationRequest;
import br.com.nexora.applications.service.ApplicationService;
import br.com.nexora.applications.service.ApplicationService.ResumeDownload;
import br.com.nexora.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Candidaturas")
public class ApplicationController {

	private final ApplicationService applicationService;

	public ApplicationController(ApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@PostMapping(path = "/jobs/{jobId}/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('STUDENT')")
	@Operation(summary = "Candidatar-se a uma vaga com o currículo da plataforma ou com um PDF",
			description = "multipart/form-data com resumeType=PLATFORM (snapshot imutável do perfil, do currículo-base "
					+ "e das competências do catálogo) ou resumeType=PDF com um único arquivo no campo file "
					+ "(até 5 MiB; extensão .pdf, tipo application/pdf e assinatura %PDF-). As opções são "
					+ "exclusivas: PDF sem arquivo dá 400 RESUME_FILE_REQUIRED e PLATFORM com arquivo, 400 "
					+ "RESUME_FILE_NOT_ALLOWED. Arquivo inválido: 400 INVALID_RESUME_FILE; acima do limite: "
					+ "413 RESUME_FILE_TOO_LARGE. Exige vínculo acadêmico aprovado com universidade ativa "
					+ "(403 STUDENT_NOT_VALIDATED) e vaga aberta de empresa ativa (404 JOB_NOT_FOUND; 409 "
					+ "JOB_NOT_ACCEPTING_APPLICATIONS). Uma candidatura por vaga (409 APPLICATION_ALREADY_EXISTS). "
					+ "404 STUDENT_PROFILE_NOT_FOUND sem perfil. O PDF nunca entra no cálculo do matching.")
	public ApplicationResponse apply(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID jobId, @Valid @ModelAttribute CreateApplicationRequest request) {
		return applicationService.apply(principal.id(), jobId, request);
	}

	@GetMapping("/applications/{applicationId}/resume")
	@PreAuthorize("hasAnyRole('STUDENT', 'COMPANY', 'ADMIN')")
	@Operation(summary = "Baixar o PDF enviado em uma candidatura",
			description = "Disponível ao estudante dono, à empresa dona da vaga e ao administrador; para os demais, "
					+ "404 APPLICATION_NOT_FOUND. Candidatura com currículo da plataforma não tem PDF: "
					+ "404 RESUME_FILE_NOT_FOUND. O arquivo nunca tem URL pública e é entregue como anexo.")
	public ResponseEntity<byte[]> downloadResume(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID applicationId) {
		ResumeDownload resume = applicationService.downloadResume(principal, applicationId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(resume.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(resume.filename(), StandardCharsets.UTF_8).build()
								.toString())
				.body(resume.content());
	}
}
