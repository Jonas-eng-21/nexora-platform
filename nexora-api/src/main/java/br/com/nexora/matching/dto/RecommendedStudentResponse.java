package br.com.nexora.matching.dto;

import br.com.nexora.matching.calculator.MatchResult;

/** Um estudante recomendado para a vaga, com a compatibilidade que justificou a posição na lista. */
public record RecommendedStudentResponse(StudentSummary student, MatchResult match) {
}
