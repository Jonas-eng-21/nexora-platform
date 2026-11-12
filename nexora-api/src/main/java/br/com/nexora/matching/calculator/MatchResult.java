package br.com.nexora.matching.calculator;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado explicável do matching: nota, pontuação por critério, competências aderentes/ausentes e resumo. */
public record MatchResult(
		@Schema(description = "Compatibilidade de 0 a 100") int score,
		@Schema(description = "Versão do algoritmo e dos critérios usados no cálculo") String algorithmVersion,
		List<CriterionResult> criteria,
		List<String> matchedRequiredSkills,
		List<String> missingRequiredSkills,
		List<String> matchedDesiredSkills,
		List<String> missingDesiredSkills,
		@Schema(description = "Justificativa em texto, montada a partir dos critérios") String summary) {

	public record CriterionResult(
			MatchCriterion criterion,
			@Schema(description = "Peso do critério, em pontos percentuais") int weight,
			@Schema(description = "Pontos obtidos, de 0 até o peso") double points,
			@Schema(description = "false quando o critério não se aplica à vaga e fica fora do cálculo")
			boolean applicable,
			String explanation) {
	}
}
