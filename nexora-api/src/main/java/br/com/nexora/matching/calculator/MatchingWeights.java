package br.com.nexora.matching.calculator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pesos dos critérios do matching, em pontos percentuais (somam 100).
 * São hipóteses de implementação configuráveis, não valores validados cientificamente.
 */
@ConfigurationProperties("nexora.matching.weights")
public record MatchingWeights(int requiredSkills, int desiredSkills, int interests, int workModelAndLocation) {

	public MatchingWeights {
		if (requiredSkills < 0 || desiredSkills < 0 || interests < 0 || workModelAndLocation < 0) {
			throw new IllegalArgumentException("Matching weights must not be negative");
		}
		int total = requiredSkills + desiredSkills + interests + workModelAndLocation;
		if (total != 100) {
			throw new IllegalArgumentException("Matching weights must add up to 100, but add up to " + total);
		}
	}

	public int of(MatchCriterion criterion) {
		return switch (criterion) {
			case REQUIRED_SKILLS -> requiredSkills;
			case DESIRED_SKILLS -> desiredSkills;
			case INTERESTS -> interests;
			case WORK_MODEL_AND_LOCATION -> workModelAndLocation;
		};
	}
}
