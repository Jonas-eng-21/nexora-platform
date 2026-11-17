package br.com.nexora.matching.semantic;

import java.util.List;

/**
 * Fronteira com o provedor de embeddings de texto (hoje o Gemini). Existe para o SDK ficar isolado e para os
 * testes usarem um provedor falso, sem rede.
 */
public interface SemanticEmbeddingProvider {

	/**
	 * Um vetor por texto, na mesma ordem. Nunca devolve parcial: ou todos, ou {@link EmbeddingUnavailableException}.
	 */
	List<float[]> embed(List<String> texts);
}
