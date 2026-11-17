package br.com.nexora.matching.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HybridScoreTests {

	@Test
	void semanticScoreIsTheClampedCosineOnAHundredScale() {
		assertThat(HybridScore.semanticScore(1.0)).isEqualTo(100);
		assertThat(HybridScore.semanticScore(0.5)).isEqualTo(50);
		assertThat(HybridScore.semanticScore(0.0)).isZero();
		assertThat(HybridScore.semanticScore(-0.7)).isZero();
		assertThat(HybridScore.semanticScore(1.3)).isEqualTo(100);
		assertThat(HybridScore.semanticScore(0.876)).isEqualTo(88);
	}

	@Test
	void hybridScoreIsTheWeightedAverageRounded() {
		// 0,80 x 78 + 0,20 x 50 = 72,4 -> 72
		assertThat(HybridScore.combine(78, 50, 20)).isEqualTo(72);
		// 0,80 x 23 + 0,20 x 100 = 38,4 -> 38
		assertThat(HybridScore.combine(23, 100, 20)).isEqualTo(38);
		// 0,80 x 77 + 0,20 x 100 = 81,6 -> 82
		assertThat(HybridScore.combine(77, 100, 20)).isEqualTo(82);
	}

	@Test
	void hybridScoreStaysBetweenZeroAndOneHundred() {
		assertThat(HybridScore.combine(100, 100, 20)).isEqualTo(100);
		assertThat(HybridScore.combine(0, 0, 20)).isZero();
	}

	@Test
	void extremeWeightsSelectOneSide() {
		assertThat(HybridScore.combine(70, 10, 0)).isEqualTo(70);
		assertThat(HybridScore.combine(70, 10, 100)).isEqualTo(10);
	}

	@Test
	void invalidWeightIsRejected() {
		assertThatThrownBy(() -> HybridScore.combine(50, 50, -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> HybridScore.combine(50, 50, 101)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nonFiniteCosineIsRejected() {
		assertThatThrownBy(() -> HybridScore.semanticScore(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
	}
}
