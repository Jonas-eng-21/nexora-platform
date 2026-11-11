package br.com.nexora.skills.controller;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.shared.dto.PageResponse;
import br.com.nexora.skills.dto.CreateSkillRequest;
import br.com.nexora.skills.dto.SkillResponse;
import br.com.nexora.skills.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/skills")
@Tag(name = "Competências")
public class SkillController {

	private static final Sort BY_NAME = Sort.by("normalizedName");

	private final SkillService skillService;

	public SkillController(SkillService skillService) {
		this.skillService = skillService;
	}

	@GetMapping
	@Operation(summary = "Consultar o catálogo de competências",
			description = "A busca ignora maiúsculas/minúsculas e acentos.")
	public PageResponse<SkillResponse> search(
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(skillService.search(search, PageResponse.pageable(page, size, BY_NAME)),
				SkillResponse::from);
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('STUDENT', 'COMPANY', 'ADMIN')")
	@Operation(summary = "Adicionar uma competência ao catálogo",
			description = "201 quando criada; 200 com a competência existente quando o nome já consta no catálogo "
					+ "(comparação sem diferenciar maiúsculas, acentos e espaços).")
	public ResponseEntity<SkillResponse> add(@Valid @RequestBody CreateSkillRequest request) {
		SkillService.AddResult result = skillService.add(request.name());
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(SkillResponse.from(result.skill()));
	}
}
