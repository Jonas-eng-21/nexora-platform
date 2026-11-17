export type ScoreType = 'STRUCTURED' | 'HYBRID' | 'FALLBACK'
export type MatchCriterion = 'REQUIRED_SKILLS' | 'DESIRED_SKILLS' | 'INTERESTS' | 'WORK_MODEL_AND_LOCATION'

export interface CriterionResult {
  criterion: MatchCriterion
  weight: number
  points: number
  applicable: boolean
  explanation: string
}

export interface MatchResult {
  score: number
  algorithmVersion: string
  criteria: CriterionResult[]
  matchedRequiredSkills: string[]
  missingRequiredSkills: string[]
  matchedDesiredSkills: string[]
  missingDesiredSkills: string[]
  summary: string
  scoreType: ScoreType
  structuredScore: number
  semanticScore: number | null
}
