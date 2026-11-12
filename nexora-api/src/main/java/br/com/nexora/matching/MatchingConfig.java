package br.com.nexora.matching;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import br.com.nexora.matching.calculator.MatchingWeights;

/** Lê os pesos de nexora.matching.weights; a aplicação não sobe se eles não somarem 100. */
@Configuration
@EnableConfigurationProperties(MatchingWeights.class)
public class MatchingConfig {
}
