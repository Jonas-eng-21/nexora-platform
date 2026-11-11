package br.com.nexora.universities.controller;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.nexora.shared.dto.PageResponse;
import br.com.nexora.universities.dto.UniversitySummaryResponse;
import br.com.nexora.universities.service.UniversityProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/universities")
@Tag(name = "Universidade")
public class UniversityCatalogController {

	private static final Sort BY_NAME = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

	private final UniversityProfileService universityProfileService;

	public UniversityCatalogController(UniversityProfileService universityProfileService) {
		this.universityProfileService = universityProfileService;
	}

	@GetMapping
	@Operation(summary = "Listar universidades ativas",
			description = "Catálogo para o estudante escolher a instituição ao solicitar vínculo.")
	public PageResponse<UniversitySummaryResponse> listActive(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(universityProfileService.listActive(PageResponse.pageable(page, size, BY_NAME)),
				UniversitySummaryResponse::from);
	}
}
