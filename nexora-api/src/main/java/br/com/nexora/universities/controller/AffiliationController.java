package br.com.nexora.universities.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.shared.dto.PageResponse;
import br.com.nexora.shared.security.AuthenticatedUser;
import br.com.nexora.universities.dto.AffiliationDecisionRequest;
import br.com.nexora.universities.dto.AffiliationResponse;
import br.com.nexora.universities.dto.ReceivedAffiliationResponse;
import br.com.nexora.universities.dto.RejectAffiliationRequest;
import br.com.nexora.universities.dto.RequestAffiliationRequest;
import br.com.nexora.universities.entity.AffiliationStatus;
import br.com.nexora.universities.service.AffiliationDecisionService;
import br.com.nexora.universities.service.AffiliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/affiliations")
@Tag(name = "Vínculo acadêmico")
public class AffiliationController {

	private static final Sort OLDEST_FIRST = Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("id"));

	private final AffiliationService affiliationService;
	private final AffiliationDecisionService affiliationDecisionService;

	public AffiliationController(AffiliationService affiliationService,
			AffiliationDecisionService affiliationDecisionService) {
		this.affiliationService = affiliationService;
		this.affiliationDecisionService = affiliationDecisionService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('STUDENT')")
	@Operation(summary = "Solicitar vínculo acadêmico com uma universidade ativa",
			description = "409 quando a universidade não está ativa (UNIVERSITY_NOT_ACTIVE), já existe solicitação "
					+ "pendente (AFFILIATION_ALREADY_PENDING) ou vínculo aprovado (AFFILIATION_ALREADY_APPROVED).")
	public AffiliationResponse request(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody RequestAffiliationRequest request) {
		return affiliationService.request(principal.id(), request.universityId());
	}

	@GetMapping("/received")
	@PreAuthorize("hasRole('UNIVERSITY')")
	@Operation(summary = "Listar pedidos de vínculo endereçados à própria universidade",
			description = "Do mais antigo para o mais recente. Use status=PENDING para a fila de análise.")
	public PageResponse<ReceivedAffiliationResponse> listReceived(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(required = false) AffiliationStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(affiliationDecisionService.listReceived(principal.id(), status,
				PageResponse.pageable(page, size, OLDEST_FIRST)), response -> response);
	}

	@PostMapping("/{affiliationId}/approve")
	@PreAuthorize("hasRole('UNIVERSITY')")
	@Operation(summary = "Aprovar um pedido de vínculo recebido",
			description = "Só a universidade destinatária. Pedido já analisado: 409 AFFILIATION_ALREADY_DECIDED.")
	public ReceivedAffiliationResponse approve(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID affiliationId, @Valid @RequestBody AffiliationDecisionRequest request) {
		return affiliationDecisionService.decideAsUniversity(principal.id(), affiliationId,
				AffiliationStatus.APPROVED, request.reason());
	}

	@PostMapping("/{affiliationId}/reject")
	@PreAuthorize("hasRole('UNIVERSITY')")
	@Operation(summary = "Rejeitar um pedido de vínculo recebido",
			description = "Só a universidade destinatária. Exige motivo, exibido ao estudante.")
	public ReceivedAffiliationResponse reject(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID affiliationId, @Valid @RequestBody RejectAffiliationRequest request) {
		return affiliationDecisionService.decideAsUniversity(principal.id(), affiliationId,
				AffiliationStatus.REJECTED, request.reason());
	}

	@GetMapping("/me")
	@PreAuthorize("hasRole('STUDENT')")
	@Operation(summary = "Consultar o próprio histórico de pedidos de vínculo, do mais recente para o mais antigo")
	public List<AffiliationResponse> listOwn(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
		return affiliationService.listOwn(principal.id());
	}
}
