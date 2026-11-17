package br.com.nexora.matching.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CosineSimilarityTests {

	@Test
	void identicalVectorsHaveSimilarityOne() {
		assertThat(CosineSimilarity.of(new float[] { 1, 2, 3 }, new float[] { 1, 2, 3 })).isEqualTo(1.0, within());
	}

	@Test
	void scaleDoesNotChangeTheSimilarity() {
		assertThat(CosineSimilarity.of(new float[] { 1, 2, 3 }, new float[] { 10, 20, 30 })).isEqualTo(1.0, within());
	}

	@Test
	void orthogonalVectorsHaveSimilarityZero() {
		assertThat(CosineSimilarity.of(new float[] { 1, 0 }, new float[] { 0, 1 })).isEqualTo(0.0, within());
	}

	@Test
	void oppositeVectorsHaveSimilarityMinusOne() {
		assertThat(CosineSimilarity.of(new float[] { 1, 2 }, new float[] { -1, -2 })).isEqualTo(-1.0, within());
	}

	@Test
	void differentLengthsAreRejected() {
		assertThatThrownBy(() -> CosineSimilarity.of(new float[] { 1, 2 }, new float[] { 1, 2, 3 }))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void zeroVectorIsRejectedInsteadOfProducingNaN() {
		assertThatThrownBy(() -> CosineSimilarity.of(new float[] { 0, 0 }, new float[] { 1, 2 }))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void emptyAndNullVectorsAreRejected() {
		assertThatThrownBy(() -> CosineSimilarity.of(new float[0], new float[0]))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CosineSimilarity.of(null, new float[] { 1 }))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nonFiniteValuesAreRejected() {
		assertThatThrownBy(() -> CosineSimilarity.of(new float[] { Float.NaN, 1 }, new float[] { 1, 1 }))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CosineSimilarity.of(new float[] { Float.POSITIVE_INFINITY, 1 }, new float[] { 1, 1 }))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static org.assertj.core.data.Offset<Double> within() {
		return org.assertj.core.data.Offset.offset(1e-6);
	}
}
