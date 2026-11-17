package br.com.nexora.matching.semantic;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.HttpOptions;

import jakarta.annotation.PreDestroy;

/**
 * Embeddings de texto pelo SDK oficial do Gemini ({@code embedContent}); sem chat nem geração de texto. Existe só
 * com {@code nexora.matching.semantic.enabled=true}; ligado e sem chave, a aplicação não sobe.
 * <p>
 * Timeout explícito e poucas tentativas com espera fixa, só para erros transitórios (429, 5xx, rede). Erros de
 * configuração ou autorização (400, 401, 403) não são repetidos. A chave nunca é registrada, e as mensagens
 * de erro do provedor não são copiadas para logs nem para o usuário.
 */
@Component
@ConditionalOnProperty(name = "nexora.matching.semantic.enabled", havingValue = "true")
public class GeminiEmbeddingProvider implements SemanticEmbeddingProvider {

	private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingProvider.class);

	private final Client client;
	private final SemanticProperties properties;
	private final EmbedContentConfig config;

	public GeminiEmbeddingProvider(SemanticProperties properties, @Value("${GEMINI_API_KEY:}") String apiKey) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException(
					"GEMINI_API_KEY must be set when GEMINI_MATCHING_ENABLED is true (set it only in the local .env or the host's secret store)");
		}
		this.properties = properties;
		this.client = Client.builder()
				.apiKey(apiKey)
				.httpOptions(HttpOptions.builder().timeout((int) properties.timeout().toMillis()).build())
				.build();
		this.config = EmbedContentConfig.builder()
				.outputDimensionality(properties.dimensions())
				.taskType("SEMANTIC_SIMILARITY")
				.build();
	}

	@Override
	public List<float[]> embed(List<String> texts) {
		EmbeddingUnavailableException last = null;
		for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
			try {
				return toVectors(client.models.embedContent(properties.model(), texts, config), texts.size());
			}
			catch (ApiException ex) {
				last = new EmbeddingUnavailableException("Gemini returned HTTP " + ex.code());
				if (!isTransient(ex.code())) {
					log.error("Gemini embeddings request rejected with HTTP {} (check the key, model and project)",
							ex.code());
					throw last;
				}
			}
			catch (RuntimeException ex) {
				// Rede, timeout ou erro interno do SDK: só o tipo é registrado, nunca a mensagem.
				last = new EmbeddingUnavailableException("Gemini request failed: " + ex.getClass().getSimpleName());
			}
			if (attempt < properties.maxAttempts()) {
				pause();
			}
		}
		throw last;
	}

	private static List<float[]> toVectors(EmbedContentResponse response, int expected) {
		List<ContentEmbedding> embeddings = response.embeddings().orElse(List.of());
		if (embeddings.size() != expected) {
			throw new EmbeddingUnavailableException("Gemini returned an unexpected number of embeddings");
		}
		List<float[]> vectors = new ArrayList<>(expected);
		for (ContentEmbedding embedding : embeddings) {
			List<Float> values = embedding.values().orElse(List.of());
			float[] vector = new float[values.size()];
			for (int i = 0; i < vector.length; i++) {
				vector[i] = values.get(i);
			}
			vectors.add(vector);
		}
		return vectors;
	}

	private static boolean isTransient(int status) {
		return status == 429 || status >= 500;
	}

	private void pause() {
		try {
			Thread.sleep(properties.retryBackoff().toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new EmbeddingUnavailableException("Interrupted while waiting to retry");
		}
	}

	@PreDestroy
	void close() {
		client.close();
	}
}
