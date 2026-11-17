package br.com.nexora.matching.semantic;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Troca o provedor real por um falso nos testes com o matching semântico ligado. */
@TestConfiguration
public class SemanticTestConfig {

	public static final String MODEL = "fake-embedding-test";
	public static final int DIMENSIONS = 16;

	@Bean
	@Primary
	FakeEmbeddingProvider fakeEmbeddingProvider() {
		return new FakeEmbeddingProvider(DIMENSIONS);
	}
}
