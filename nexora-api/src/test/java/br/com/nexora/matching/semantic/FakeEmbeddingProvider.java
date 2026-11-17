package br.com.nexora.matching.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provedor falso, sem rede: o vetor é uma sacola de palavras em buckets fixos, então textos com vocabulário
 * parecido têm cosseno alto. Registra cada chamada em lote para os testes contarem as requisições.
 */
public class FakeEmbeddingProvider implements SemanticEmbeddingProvider {

	private final int dimensions;
	private final List<List<String>> calls = new CopyOnWriteArrayList<>();
	private volatile boolean failing;

	public FakeEmbeddingProvider(int dimensions) {
		this.dimensions = dimensions;
	}

	@Override
	public List<float[]> embed(List<String> texts) {
		calls.add(List.copyOf(texts));
		if (failing) {
			throw new EmbeddingUnavailableException("fake provider is failing");
		}
		List<float[]> vectors = new ArrayList<>();
		for (String text : texts) {
			vectors.add(vectorOf(text));
		}
		return vectors;
	}

	float[] vectorOf(String text) {
		float[] vector = new float[dimensions];
		for (String word : text.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
			if (!word.isBlank()) {
				vector[Math.floorMod(word.hashCode(), dimensions)] += 1;
			}
		}
		return vector;
	}

	public void failing(boolean failing) {
		this.failing = failing;
	}

	public void reset() {
		calls.clear();
		failing = false;
	}

	/** Cada elemento é um lote enviado ao provedor. */
	public List<List<String>> calls() {
		return calls;
	}

	public int totalTexts() {
		return calls.stream().mapToInt(List::size).sum();
	}
}
