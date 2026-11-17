package br.com.nexora.matching.semantic;

/** O provedor de embeddings não devolveu um resultado utilizável (timeout, limite, erro ou resposta inválida). */
public class EmbeddingUnavailableException extends RuntimeException {

	public EmbeddingUnavailableException(String message) {
		super(message);
	}

	public EmbeddingUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
