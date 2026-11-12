package br.com.nexora.matching.dto;

import java.util.UUID;

import br.com.nexora.matching.calculator.MatchResult;

/** Compatibilidade do estudante autenticado com uma vaga. */
public record JobMatchResponse(UUID jobId, String jobTitle, MatchResult match) {
}
