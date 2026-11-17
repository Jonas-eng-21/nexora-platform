package br.com.nexora.matching.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.springframework.stereotype.Service;

/**
 * Score semântico (0 a 100) entre um texto âncora e vários outros: o cosseno entre embeddings, nunca uma nota
 * de LLM. Os embeddings são obtidos em lote e reaproveitados, então uma página de recomendações não faz uma
 * chamada externa por linha. Sem embedding para algum par, o resultado dele é vazio (fallback estruturado).
 */
@Service
public class SemanticSimilarityService {

	private final SemanticProperties properties;
	private final EmbeddingService embeddingService;

	public SemanticSimilarityService(SemanticProperties properties, EmbeddingService embeddingService) {
		this.properties = properties;
		this.embeddingService = embeddingService;
	}

	public boolean isEnabled() {
		return properties.enabled();
	}

	public int semanticWeight() {
		return properties.semanticWeight();
	}

	public int maxTextLength() {
		return properties.maxTextLength();
	}

	/** Um resultado por texto de {@code others}, na mesma ordem. */
	public List<OptionalInt> scores(String anchor, List<String> others) {
		List<String> all = new ArrayList<>(others.size() + 1);
		all.add(anchor);
		all.addAll(others);
		Map<String, float[]> vectors = embeddingService.embed(all);

		float[] anchorVector = vectors.get(anchor);
		List<OptionalInt> scores = new ArrayList<>(others.size());
		for (String other : others) {
			float[] vector = vectors.get(other);
			if (anchorVector == null || vector == null) {
				scores.add(OptionalInt.empty());
				continue;
			}
			try {
				scores.add(OptionalInt.of(HybridScore.semanticScore(CosineSimilarity.of(anchorVector, vector))));
			}
			catch (IllegalArgumentException ex) {
				scores.add(OptionalInt.empty());
			}
		}
		return scores;
	}
}
