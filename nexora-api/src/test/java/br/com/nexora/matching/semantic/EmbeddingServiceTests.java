package br.com.nexora.matching.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reaproveitamento por hash + modelo + dimensão, lotes, teto por requisição e falhas. Grava de verdade (sem rollback). */
@SpringBootTest
class EmbeddingServiceTests {

	@Autowired
	private EmbeddingStore store;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String model;
	private FakeEmbeddingProvider provider;

	@BeforeEach
	void setUp() {
		model = "embedding-service-test-" + UUID.randomUUID();
		provider = new FakeEmbeddingProvider(16);
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("delete from text_embeddings where model like 'embedding-service-test-%'");
	}

	private EmbeddingService service(String model, int dimensions, int maxNew, SemanticEmbeddingProvider provider) {
		return new EmbeddingService(properties(model, dimensions, maxNew), store, Optional.ofNullable(provider));
	}

	private static SemanticProperties properties(String model, int dimensions, int maxNew) {
		return new SemanticProperties(true, model, dimensions, 20, Duration.ofSeconds(5), 1, Duration.ZERO, maxNew,
				4000);
	}

	@Test
	void sameTextWithSameConfigurationIsEmbeddedOnlyOnce() {
		EmbeddingService service = service(model, 16, 100, provider);

		Map<String, float[]> first = service.embed(List.of("backend java"));
		Map<String, float[]> second = service.embed(List.of("backend java"));

		assertThat(provider.calls()).hasSize(1);
		assertThat(second.get("backend java")).containsExactly(first.get("backend java"));
	}

	@Test
	void missingTextsGoToTheProviderInASingleBatchWithoutDuplicates() {
		EmbeddingService service = service(model, 16, 100, provider);

		Map<String, float[]> result = service.embed(List.of("alpha", "beta", "alpha", "gamma"));

		assertThat(provider.calls()).hasSize(1);
		assertThat(provider.calls().get(0)).containsExactlyInAnyOrder("alpha", "beta", "gamma");
		assertThat(result).containsOnlyKeys("alpha", "beta", "gamma");
	}

	@Test
	void onlyTheNewTextsAreSentWhenSomeAreAlreadyCached() {
		EmbeddingService service = service(model, 16, 100, provider);
		service.embed(List.of("alpha", "beta"));
		provider.reset();

		service.embed(List.of("alpha", "beta", "gamma"));

		assertThat(provider.calls()).hasSize(1);
		assertThat(provider.calls().get(0)).containsExactly("gamma");
	}

	@Test
	void changedTextIsNotReusedFromTheOldOne() {
		EmbeddingService service = service(model, 16, 100, provider);
		service.embed(List.of("java backend"));

		service.embed(List.of("java backend senior"));

		assertThat(provider.calls()).hasSize(2);
	}

	@Test
	void changedModelOrDimensionIsNotReused() {
		service(model, 16, 100, provider).embed(List.of("java backend"));

		service(model + "-other", 16, 100, provider).embed(List.of("java backend"));
		FakeEmbeddingProvider smaller = new FakeEmbeddingProvider(8);
		Map<String, float[]> result = service(model, 8, 100, smaller).embed(List.of("java backend"));

		assertThat(provider.calls()).hasSize(2);
		assertThat(smaller.calls()).hasSize(1);
		assertThat(result.get("java backend")).hasSize(8);
	}

	@Test
	void providerFailureYieldsNoEmbeddingAndDoesNotThrow() {
		provider.failing(true);
		EmbeddingService service = service(model, 16, 100, provider);

		Map<String, float[]> result = service.embed(List.of("alpha"));

		assertThat(result).isEmpty();
		provider.failing(false);
		service.embed(List.of("alpha"));
		assertThat(provider.calls()).hasSize(2); // a falha não deixou nada guardado
	}

	@Test
	void textsBeyondThePerRequestLimitAreLeftWithoutEmbedding() {
		EmbeddingService service = service(model, 16, 2, provider);

		Map<String, float[]> result = service.embed(List.of("a1", "b2", "c3", "d4", "e5"));

		assertThat(provider.totalTexts()).isEqualTo(2);
		assertThat(result).hasSize(2);
	}

	@Test
	void invalidVectorsFromTheProviderAreDiscarded() {
		SemanticEmbeddingProvider wrongDimension = texts -> texts.stream().map(t -> new float[] { 1, 2, 3 }).toList();
		SemanticEmbeddingProvider zeros = texts -> texts.stream().map(t -> new float[16]).toList();
		SemanticEmbeddingProvider notFinite = texts -> texts.stream().map(t -> {
			float[] v = new float[16];
			v[0] = Float.NaN;
			return v;
		}).toList();
		SemanticEmbeddingProvider wrongCount = texts -> List.of(new float[16]);

		for (SemanticEmbeddingProvider bad : List.of(wrongDimension, zeros, notFinite, wrongCount)) {
			assertThat(service(model, 16, 100, bad).embed(List.of("alpha", "beta"))).isEmpty();
		}
	}

	@Test
	void withoutAProviderOnlyCachedEmbeddingsAreReturned() {
		service(model, 16, 100, provider).embed(List.of("alpha"));

		Map<String, float[]> result = service(model, 16, 100, null).embed(List.of("alpha", "beta"));

		assertThat(result).containsOnlyKeys("alpha");
	}

	@Test
	void vectorsSurviveTheRoundTripThroughTheDatabase() {
		EmbeddingService service = service(model, 16, 100, provider);
		float[] original = service.embed(List.of("round trip")).get("round trip");

		float[] stored = service.embed(List.of("round trip")).get("round trip");

		assertThat(stored).containsExactly(original);
	}
}
