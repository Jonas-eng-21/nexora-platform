package br.com.nexora.matching.calculator;

/** Como o score de um resultado foi obtido. */
public enum ScoreType {
	/** Só critérios estruturados (matching semântico desligado ou sem dados para comparar). */
	STRUCTURED,
	/** Estruturado combinado com a similaridade semântica dos embeddings. */
	HYBRID,
	/** Semântico ligado, porém indisponível para este par: vale o score estruturado. */
	FALLBACK
}
