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

/** Sem `match` o score é "indisponível": nunca vira zero. Zero real continua sendo zero. */
export function describeMatch(match: MatchResult | null | undefined): ScoreView {
  if (!match) return { state: 'unavailable' }
  const bandLabel = match.score >= 70 ? 'Alta afinidade' : match.score >= 40 ? 'Afinidade média' : 'Baixa afinidade'
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
