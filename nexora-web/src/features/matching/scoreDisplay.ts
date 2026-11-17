import type { MatchCriterion, MatchResult, ScoreType } from './types'

export type ScoreView =
  | { state: 'unavailable' }
  | {
      state: 'ready'
      score: number
      bandLabel: string
      typeLabel: string
      typeNote: string
      structured: number
      semantic: number | null
    }

const TYPE_LABEL: Record<ScoreType, string> = {
  STRUCTURED: 'Cálculo estruturado',
  HYBRID: 'Estruturado e semântico',
  FALLBACK: 'Cálculo estruturado (semântica indisponível)',
}

const TYPE_NOTE: Record<ScoreType, string> = {
  STRUCTURED: 'Nota calculada só por critérios definidos: competências, área de interesse, modelo de trabalho e localização.',
  HYBRID: 'Nota que combina os critérios estruturados com a similaridade semântica entre o currículo-base e a vaga.',
  FALLBACK: 'A análise semântica está indisponível no momento; vale apenas a nota estruturada.',
}

export const CRITERION_LABEL: Record<MatchCriterion, string> = {
  REQUIRED_SKILLS: 'Competências obrigatórias',
  DESIRED_SKILLS: 'Competências desejáveis',
  INTERESTS: 'Área de interesse',
  WORK_MODEL_AND_LOCATION: 'Modelo de trabalho e localização',
}

export const bandFor = (score: number): string =>
  score >= 70 ? 'Alta afinidade' : score >= 40 ? 'Afinidade média' : 'Baixa afinidade'

export type NumericScoreView = { state: 'insufficient' } | { state: 'ready'; score: number; bandLabel: string }

/** `matchScore` da lista de candidaturas (US-30): nulo = "sem dados suficientes", nunca zero. */
export function describeScore(score: number | null | undefined): NumericScoreView {
  if (score === null || score === undefined) return { state: 'insufficient' }
  return { state: 'ready', score, bandLabel: bandFor(score) }
}

/** Sem `match` o score é "indisponível": nunca vira zero. Zero real continua sendo zero. */
export function describeMatch(match: MatchResult | null | undefined): ScoreView {
  if (!match) return { state: 'unavailable' }
  const bandLabel = bandFor(match.score)
  return {
    state: 'ready',
    score: match.score,
    bandLabel,
    typeLabel: TYPE_LABEL[match.scoreType],
    typeNote: TYPE_NOTE[match.scoreType],
    structured: match.structuredScore,
    semantic: match.semanticScore,
  }
}
