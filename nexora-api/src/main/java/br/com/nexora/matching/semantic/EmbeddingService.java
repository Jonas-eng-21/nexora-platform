package br.com.nexora.matching.semantic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Entrega embeddings de textos reaproveitando o que já foi calculado: a chave é o hash do texto canônico mais o
 * modelo e a dimensão. Só os textos novos vão ao provedor, em lotes, e no máximo
 * {@code maxNewEmbeddingsPerRequest} por chamada; o que não puder ser obtido simplesmente não vem no resultado
 * (quem chama cai no score estruturado). Nunca registra o conteúdo dos textos nem das respostas.
 */
@Service
public class EmbeddingService {

	/** Limite de textos por requisição em lote ao provedor. */
	static final int BATCH_SIZE = 100;

	private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

	private final SemanticProperties properties;
	private final EmbeddingStore store;
	private final java.util.Optional<SemanticEmbeddingProvider> provider;

	public EmbeddingService(SemanticProperties properties, EmbeddingStore store,
			java.util.Optional<SemanticEmbeddingProvider> provider) {
		this.properties = properties;
		this.store = store;
		this.provider = provider;
	}

	/** Embeddings disponíveis para os textos (chave = o próprio texto). Textos sem embedding ficam de fora. */
	public Map<String, float[]> embed(List<String> texts) {
		Map<String, String> hashByText = new LinkedHashMap<>();
		texts.stream().distinct().forEach(text -> hashByText.put(text, hash(text)));

		Map<String, float[]> byHash = new HashMap<>(
				store.find(Set.copyOf(hashByText.values()), properties.model(), properties.dimensions()));

		List<String> missing = hashByText.keySet().stream()
				.filter(text -> !byHash.containsKey(hashByText.get(text))).toList();
		if (!missing.isEmpty()) {
			fetchMissing(missing, hashByText, byHash);
		}

		Map<String, float[]> result = new HashMap<>();
		hashByText.forEach((text, hash) -> {
			float[] vector = byHash.get(hash);
			if (vector != null) {
				result.put(text, vector);
			}
		});
		return result;
	}

	private void fetchMissing(List<String> missing, Map<String, String> hashByText, Map<String, float[]> byHash) {
		if (provider.isEmpty()) {
			return;
		}
		List<String> toFetch = new ArrayList<>(missing);
		if (toFetch.size() > properties.maxNewEmbeddingsPerRequest()) {
			log.warn("Semantic matching: {} new texts exceed the per-request limit of {}; the rest uses the structured score",
					toFetch.size(), properties.maxNewEmbeddingsPerRequest());
			toFetch = toFetch.subList(0, properties.maxNewEmbeddingsPerRequest());
		}
		for (int from = 0; from < toFetch.size(); from += BATCH_SIZE) {
			List<String> batch = toFetch.subList(from, Math.min(from + BATCH_SIZE, toFetch.size()));
			List<float[]> vectors;
			try {
				vectors = provider.get().embed(batch);
			}
			catch (EmbeddingUnavailableException ex) {
				log.warn("Semantic matching: embedding provider unavailable ({}); using the structured score",
						ex.getMessage());
				return;
			}
			if (vectors == null || vectors.size() != batch.size()) {
				log.warn("Semantic matching: provider returned an unexpected number of vectors; using the structured score");
				return;
			}
			for (int i = 0; i < batch.size(); i++) {
				float[] vector = vectors.get(i);
				if (!isValid(vector)) {
					log.warn("Semantic matching: provider returned an invalid vector; using the structured score");
					return;
				}
				String hash = hashByText.get(batch.get(i));
				byHash.put(hash, vector);
				store.save(hash, properties.model(), properties.dimensions(), vector);
			}
		}
	}

	private boolean isValid(float[] vector) {
		if (vector == null || vector.length != properties.dimensions()) {
			return false;
		}
		boolean nonZero = false;
		for (float value : vector) {
			if (!Float.isFinite(value)) {
				return false;
			}
			nonZero |= value != 0;
		}
		return nonZero;
	}

	static String hash(String text) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
