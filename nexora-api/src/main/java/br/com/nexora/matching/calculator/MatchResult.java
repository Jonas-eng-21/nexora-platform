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
		@Schema(description = "Justificativa em texto, montada a partir dos critérios") String summary,
		@Schema(description = "STRUCTURED (só critérios), HYBRID (com similaridade semântica) ou FALLBACK "
				+ "(semântica indisponível: vale o estruturado)") ScoreType scoreType,
		@Schema(description = "Score dos critérios estruturados; igual a score quando scoreType não é HYBRID")
		int structuredScore,
		@Schema(description = "Similaridade semântica de 0 a 100; nulo quando scoreType não é HYBRID")
		Integer semanticScore) {

	/** Resultado estruturado puro (o calculador determinístico). */
	public MatchResult(int score, String algorithmVersion, List<CriterionResult> criteria,
			List<String> matchedRequiredSkills, List<String> missingRequiredSkills, List<String> matchedDesiredSkills,
			List<String> missingDesiredSkills, String summary) {
		this(score, algorithmVersion, criteria, matchedRequiredSkills, missingRequiredSkills, matchedDesiredSkills,
				missingDesiredSkills, summary, ScoreType.STRUCTURED, score, null);
	}

	/** Mesmo resultado com o score híbrido; os critérios estruturados continuam sendo a justificativa. */
	public MatchResult withSemantic(int hybridScore, int semanticScore) {
		return new MatchResult(hybridScore, algorithmVersion, criteria, matchedRequiredSkills, missingRequiredSkills,
				matchedDesiredSkills, missingDesiredSkills,
				summary + " Similaridade semântica de " + semanticScore + "/100 combinada ao score estruturado ("
						+ structuredScore + ").",
				ScoreType.HYBRID, structuredScore, semanticScore);
	}

	/** Mesmo resultado estruturado, marcado como fallback porque a análise semântica não pôde ser feita. */
	public MatchResult asFallback() {
		return new MatchResult(score, algorithmVersion, criteria, matchedRequiredSkills, missingRequiredSkills,
				matchedDesiredSkills, missingDesiredSkills,
				summary + " A análise semântica está indisponível no momento; vale apenas o score estruturado.",
				ScoreType.FALLBACK, structuredScore, null);
	}

	public record CriterionResult(
			MatchCriterion criterion,
			@Schema(description = "Peso do critério, em pontos percentuais") int weight,
			@Schema(description = "Pontos obtidos, de 0 até o peso") double points,
			@Schema(description = "false quando o critério não se aplica à vaga e fica fora do cálculo")
			boolean applicable,
			String explanation) {
	}
}
