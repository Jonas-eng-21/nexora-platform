package br.com.nexora.shared.domain;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/areas")
@Tag(name = "Áreas de atuação")
public class AreaCatalogController {

	public record AreaResponse(ProfessionalArea code, String label) {
	}

	@GetMapping
	@Operation(summary = "Listar o catálogo fixo de áreas de atuação",
			description = "Use o campo code na área da vaga e nas áreas de interesse do currículo; label é o nome de exibição.")
	public List<AreaResponse> list() {
		return Arrays.stream(ProfessionalArea.values())
				.map(area -> new AreaResponse(area, area.getLabel()))
				.toList();
	}
}
