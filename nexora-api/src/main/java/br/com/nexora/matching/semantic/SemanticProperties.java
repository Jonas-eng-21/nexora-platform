package br.com.nexora.matching.semantic;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do matching semântico ({@code nexora.matching.semantic}). A chave da API não fica aqui: o
 * provedor a lê direto da variável de ambiente, para ela nunca aparecer em {@code toString} nem em logs.
 *
 * @param enabled                    liga o score híbrido; desligado, o matching estruturado segue como antes
 * @param semanticWeight             peso do score semântico no híbrido, em pontos percentuais (hipótese técnica)
 * @param maxNewEmbeddingsPerRequest teto de textos novos enviados ao provedor por requisição de recomendação;
 *                                   o que passar disso sai como fallback estruturado
 */
@ConfigurationProperties("nexora.matching.semantic")
public record SemanticProperties(boolean enabled, String model, int dimensions, int semanticWeight, Duration timeout,
		int maxAttempts, Duration retryBackoff, int maxNewEmbeddingsPerRequest, int maxTextLength) {

	public SemanticProperties {
		model = model == null || model.isBlank() ? "gemini-embedding-001" : model;
		dimensions = dimensions <= 0 ? 768 : dimensions;
		if (semanticWeight < 0 || semanticWeight > 100) {
			throw new IllegalArgumentException("Semantic weight must be between 0 and 100");
		}
		timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
		maxAttempts = maxAttempts <= 0 ? 2 : maxAttempts;
		retryBackoff = retryBackoff == null ? Duration.ofMillis(500) : retryBackoff;
		maxNewEmbeddingsPerRequest = maxNewEmbeddingsPerRequest <= 0 ? 100 : maxNewEmbeddingsPerRequest;
		maxTextLength = maxTextLength <= 0 ? 4000 : maxTextLength;
	}
}
