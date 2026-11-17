package br.com.nexora.matching.semantic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class GeminiEmbeddingProviderTests {

	private static final SemanticProperties PROPERTIES = new SemanticProperties(true, "gemini-embedding-001", 768, 20,
			Duration.ofSeconds(5), 2, Duration.ZERO, 100, 4000);

	@Test
	void enabledWithoutAKeyFailsAtStartupInsteadOfRunningSilently() {
		assertThatThrownBy(() -> new GeminiEmbeddingProvider(PROPERTIES, ""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("GEMINI_API_KEY");
		assertThatThrownBy(() -> new GeminiEmbeddingProvider(PROPERTIES, "   "))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new GeminiEmbeddingProvider(PROPERTIES, null))
				.isInstanceOf(IllegalStateException.class);
	}
}
