package br.com.nexora.matching.dto;

import br.com.nexora.jobs.dto.JobResponse;
import br.com.nexora.matching.calculator.MatchResult;

/** Uma vaga recomendada ao estudante, com a compatibilidade que justificou a posição na lista. */
public record RecommendedJobResponse(JobResponse job, MatchResult match) {
}
