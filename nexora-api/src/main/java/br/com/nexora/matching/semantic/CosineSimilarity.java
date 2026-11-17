package br.com.nexora.matching.semantic;

/** Similaridade do cosseno entre dois vetores: função pura, sem chamadas externas. */
public final class CosineSimilarity {

	private CosineSimilarity() {
	}

	/**
	 * {@code dot(a, b) / (norm(a) * norm(b))}, entre -1 e 1.
	 *
	 * @throws IllegalArgumentException se algum vetor for nulo, vazio, tiver valor não finito ou norma zero,
	 *                                  ou se os comprimentos forem diferentes (nunca devolve NaN)
	 */
	public static double of(float[] a, float[] b) {
		if (a == null || b == null || a.length == 0 || b.length == 0) {
			throw new IllegalArgumentException("Embedding vectors must not be null or empty");
		}
		if (a.length != b.length) {
			throw new IllegalArgumentException("Embedding vectors must have the same length");
		}
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			if (!Float.isFinite(a[i]) || !Float.isFinite(b[i])) {
				throw new IllegalArgumentException("Embedding vectors must contain only finite values");
			}
			dot += (double) a[i] * b[i];
			normA += (double) a[i] * a[i];
			normB += (double) b[i] * b[i];
		}
		if (normA == 0 || normB == 0) {
			throw new IllegalArgumentException("Embedding vectors must not be zero vectors");
		}
		double cosine = dot / (Math.sqrt(normA) * Math.sqrt(normB));
		return Math.max(-1.0, Math.min(1.0, cosine));
	}
}
