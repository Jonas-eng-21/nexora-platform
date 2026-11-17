package br.com.nexora.matching.semantic;

/**
 * Composição do score híbrido. Os pesos são hipóteses técnicas configuráveis, não validação científica:
 * {@code híbrido = round((100 - w) / 100 x estruturado + w / 100 x semântico)}, com w padrão de 20.
 */
public final class HybridScore {

	private HybridScore() {
	}

	/** Contribuição semântica: {@code 100 x clamp(cosseno, 0, 1)}, arredondada. */
	public static int semanticScore(double cosine) {
		if (!Double.isFinite(cosine)) {
			throw new IllegalArgumentException("Cosine similarity must be finite");
		}
		return (int) Math.round(100 * Math.max(0.0, Math.min(1.0, cosine)));
	}

	/** Média ponderada dos dois scores (0 a 100), com {@code semanticWeight} em pontos percentuais. */
	public static int combine(int structuredScore, int semanticScore, int semanticWeight) {
		if (semanticWeight < 0 || semanticWeight > 100) {
			throw new IllegalArgumentException("Semantic weight must be between 0 and 100");
		}
		double combined = ((100 - semanticWeight) * structuredScore + semanticWeight * semanticScore) / 100.0;
		return Math.max(0, Math.min(100, (int) Math.round(combined)));
	}
}
