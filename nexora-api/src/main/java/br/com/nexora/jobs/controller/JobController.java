package br.com.nexora.jobs.controller;

import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.jobs.dto.CreateJobRequest;
import br.com.nexora.jobs.dto.JobResponse;
import br.com.nexora.jobs.entity.JobLevel;
import br.com.nexora.jobs.entity.JobStatus;
import br.com.nexora.shared.domain.ProfessionalArea;
import br.com.nexora.shared.domain.WorkModel;
import br.com.nexora.jobs.service.JobService;
import br.com.nexora.shared.dto.PageResponse;
import br.com.nexora.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Vagas")
public class JobController {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

	private final JobService jobService;

	public JobController(JobService jobService) {
		this.jobService = jobService;
	}

	@GetMapping
	@Operation(summary = "Pesquisar vagas abertas de empresas ativas",
			description = "Oculta vagas encerradas, excluídas logicamente ou de empresas suspensas. "
					+ "Permite filtrar por termo livre (search), área, localização, modelo de trabalho, nível e competência.")
	public PageResponse<JobResponse> searchOpen(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) ProfessionalArea area,
			@RequestParam(required = false) String location,
			@RequestParam(required = false) WorkModel workModel,
			@RequestParam(required = false) JobLevel level,
			@RequestParam(required = false) UUID skillId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(
				jobService.searchOpen(search, area, location, workModel, level, skillId,
						PageResponse.pageable(page, size, NEWEST_FIRST)),
				response -> response);
	}

	@GetMapping("/{jobId}")
	@Operation(summary = "Consultar o detalhe de uma vaga",
			description = "Vagas excluídas logicamente retornam 404 JOB_NOT_FOUND. Vagas encerradas ou de empresa "
					+ "suspensa só são visíveis para a própria empresa dona e para o administrador.")
	public JobResponse getDetail(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID jobId) {
		return jobService.getDetail(principal, jobId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('COMPANY')")
	@Operation(summary = "Publicar uma vaga da própria empresa",
			description = "A empresa é a da conta autenticada (404 COMPANY_PROFILE_NOT_FOUND sem perfil). "
					+ "Competências inexistentes: 400 SKILL_NOT_FOUND; a mesma competência como obrigatória "
					+ "e desejável: 400 JOB_SKILL_CONFLICT. Status padrão: OPEN.")
	public JobResponse create(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody CreateJobRequest request) {
		return jobService.create(principal.id(), request);
	}

	@GetMapping("/mine")
	@PreAuthorize("hasRole('COMPANY')")
	@Operation(summary = "Listar as vagas não excluídas da própria empresa, da mais recente para a mais antiga")
	public PageResponse<JobResponse> listOwn(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(required = false) JobStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(
				jobService.listOwn(principal.id(), status, PageResponse.pageable(page, size, NEWEST_FIRST)),
				response -> response);
	}

	@PutMapping("/{jobId}")
	@PreAuthorize("hasRole('COMPANY')")
	@Operation(summary = "Editar integralmente uma vaga da própria empresa",
			description = "404 JOB_NOT_FOUND quando a vaga não existe, está excluída logicamente ou pertence a outra empresa.")
	public JobResponse update(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID jobId, @Valid @RequestBody CreateJobRequest request) {
		return jobService.updateAsCompany(principal.id(), jobId, request);
	}

	@PostMapping("/{jobId}/close")
	@PreAuthorize("hasRole('COMPANY')")
	@Operation(summary = "Encerrar uma vaga da própria empresa",
			description = "Vagas encerradas (CLOSED) deixam de receber novas candidaturas.")
	public JobResponse close(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID jobId) {
		return jobService.closeAsCompany(principal.id(), jobId);
	}

	@PostMapping("/{jobId}/reopen")
	@PreAuthorize("hasRole('COMPANY')")
	@Operation(summary = "Reabrir uma vaga encerrada da própria empresa")
	public JobResponse reopen(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID jobId) {
		return jobService.reopenAsCompany(principal.id(), jobId);
	}

	@DeleteMapping("/{jobId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('COMPANY')")
	@Operation(summary = "Excluir logicamente uma vaga da própria empresa",
			description = "Encerra a vaga e oculta de consultas sem apagar candidaturas vinculadas.")
	public void delete(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID jobId) {
		jobService.deleteAsCompany(principal.id(), jobId);
	}
}
