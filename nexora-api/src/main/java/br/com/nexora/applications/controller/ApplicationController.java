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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.applications.dto.ApplicationResponse;
import br.com.nexora.applications.dto.ApplicationReactivationResponse;
import br.com.nexora.applications.dto.ApplicationStatusResponse;
import br.com.nexora.applications.dto.ApplicationSummaryResponse;
import br.com.nexora.applications.dto.ChangeApplicationStatusRequest;
import br.com.nexora.applications.dto.CreateApplicationRequest;
import br.com.nexora.applications.dto.JobApplicationResponse;
import br.com.nexora.applications.entity.ApplicationStatus;
import br.com.nexora.applications.service.ApplicationService;
import br.com.nexora.applications.service.ApplicationService.ResumeDocument;
import br.com.nexora.applications.service.ApplicationReactivationService;
import br.com.nexora.applications.service.ApplicationStatusService;
import br.com.nexora.applications.service.JobApplicationService;
import br.com.nexora.shared.dto.PageResponse;
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
	private final JobApplicationService jobApplicationService;
	private final ApplicationStatusService applicationStatusService;
	private final ApplicationReactivationService applicationReactivationService;

	public ApplicationController(ApplicationService applicationService,
			JobApplicationService jobApplicationService, ApplicationStatusService applicationStatusService,
			ApplicationReactivationService applicationReactivationService) {
		this.applicationService = applicationService;
		this.jobApplicationService = jobApplicationService;
		this.applicationStatusService = applicationStatusService;
		this.applicationReactivationService = applicationReactivationService;
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

	@GetMapping("/jobs/{jobId}/applications")
	@PreAuthorize("hasRole('COMPANY')")
	@Operation(summary = "Listar as candidaturas recebidas por uma vaga da própria empresa",
			description = "Todos os status (inclusive suspensas e rejeitadas), da mais recente para a mais antiga "
					+ "(appliedAt e id decrescentes); filtro opcional por status; página inicial 0, tamanho padrão "
					+ "10 e máximo 50. Cada item traz o resumo autorizado do estudante (sem e-mail) e matchScore, a "
					+ "compatibilidade ATUAL de 0 a 100 calculada com o currículo-base (nunca com o PDF), ou nulo "
					+ "quando o currículo-base não tem competências nem áreas de interesse. Não traz o currículo "
					+ "enviado: use GET /api/v1/applications/{id}/resume. 404 JOB_NOT_FOUND para vaga de outra "
					+ "empresa, excluída ou inexistente.")
	public PageResponse<JobApplicationResponse> listJobApplications(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID jobId,
			@RequestParam(required = false) ApplicationStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return jobApplicationService.listForOwnJob(principal.id(), jobId, status, page, size);
	}

	@PatchMapping("/applications/{applicationId}/status")
	@PreAuthorize("hasRole('COMPANY')")
	@Operation(summary = "Atualizar o andamento de uma candidatura da própria empresa",
			description = "Transições permitidas: SUBMITTED para IN_REVIEW, APPROVED ou REJECTED, e IN_REVIEW para "
					+ "APPROVED ou REJECTED. APPROVED e REJECTED são finais; SUSPENDED não pode ser atribuído "
					+ "(409 INVALID_APPLICATION_STATUS_TRANSITION). Candidatura suspensa não avança "
					+ "(409 APPLICATION_SUSPENDED) e o estudante precisa estar academicamente elegível "
					+ "(403 STUDENT_NOT_VALIDATED). Rejeitar exige motivo (400 REJECTION_REASON_REQUIRED); o motivo "
					+ "tem até 500 caracteres. Candidatura de outra empresa ou inexistente: 404 "
					+ "APPLICATION_NOT_FOUND.")
	public ApplicationStatusResponse changeStatus(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID applicationId, @Valid @RequestBody ChangeApplicationStatusRequest request) {
		return applicationStatusService.changeAsCompany(principal.id(), applicationId, request);
	}

	@PostMapping("/applications/{applicationId}/reactivate")
	@PreAuthorize("hasRole('COMPANY')")
	@Operation(summary = "Reativar uma candidatura suspensa da própria empresa",
			description = "Sem corpo. Restaura exatamente o estado guardado na suspensão (SUBMITTED ou IN_REVIEW) e "
					+ "limpa os dados da suspensão, preservando o currículo e a última decisão. Exige candidatura "
					+ "suspensa (409 APPLICATION_NOT_SUSPENDED), estado guardado válido (409 "
					+ "INVALID_SUSPENSION_STATE), conta do estudante ativa (409 STUDENT_ACCOUNT_NOT_ACTIVE), "
					+ "estudante academicamente elegível (403 STUDENT_NOT_VALIDATED) e vaga aberta, não excluída e de "
					+ "empresa ativa (409 JOB_NOT_AVAILABLE). Candidatura de outra empresa ou inexistente: 404 "
					+ "APPLICATION_NOT_FOUND. Nunca acontece sozinha.")
	public ApplicationReactivationResponse reactivate(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID applicationId) {
		return applicationReactivationService.reactivateAsCompany(principal.id(), applicationId);
	}

	@GetMapping("/applications/me")
	@PreAuthorize("hasRole('STUDENT')")
	@Operation(summary = "Listar as próprias candidaturas",
			description = "Todas as candidaturas do estudante autenticado, inclusive suspensas, rejeitadas e "
					+ "finalizadas, da mais recente para a mais antiga (appliedAt e id decrescentes). Filtro opcional "
					+ "por status (SUBMITTED, IN_REVIEW, APPROVED, REJECTED, SUSPENDED; outro valor dá 400). Página "
					+ "inicial 0, tamanho padrão 10 e máximo 50. Não traz o currículo enviado: use "
					+ "GET /api/v1/applications/{id}/resume. Sem perfil de estudante, a lista vem vazia.")
	public PageResponse<ApplicationSummaryResponse> listOwn(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(required = false) ApplicationStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return applicationService.listOwn(principal.id(), status, page, size);
	}

	@GetMapping("/applications/{applicationId}/resume")
	@PreAuthorize("hasAnyRole('STUDENT', 'COMPANY', 'ADMIN')")
	@Operation(summary = "Consultar o currículo enviado em uma candidatura",
			description = "Disponível ao estudante dono, à empresa dona da vaga e ao administrador; para os demais, "
					+ "404 APPLICATION_NOT_FOUND. Candidatura PLATFORM: o snapshot imutável em JSON (perfil, "
					+ "currículo-base e competências no momento do envio). Candidatura PDF: o arquivo privado como "
					+ "anexo (application/pdf), sem URL pública. O currículo de uma candidatura não pode ser editado."
	)
	public ResponseEntity<?> readResume(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID applicationId) {
		return switch (applicationService.readResume(principal, applicationId)) {
			case ResumeDocument.Snapshot resume -> ResponseEntity.ok()
					.contentType(MediaType.APPLICATION_JSON)
					.body(resume.snapshot());
			case ResumeDocument.Pdf resume -> ResponseEntity.ok()
					.contentType(MediaType.parseMediaType(resume.contentType()))
					.header(HttpHeaders.CONTENT_DISPOSITION,
							ContentDisposition.attachment().filename(resume.filename(), StandardCharsets.UTF_8)
									.build().toString())
					.body(resume.content());
		};
	}
}
