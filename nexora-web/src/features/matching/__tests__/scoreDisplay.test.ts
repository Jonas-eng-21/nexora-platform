import { describe, expect, it } from 'vitest'
import { describeMatch } from '../scoreDisplay'
import type { MatchResult } from '../types'

const base: MatchResult = {
  score: 80, algorithmVersion: 'v1', criteria: [], matchedRequiredSkills: [], missingRequiredSkills: [],
  matchedDesiredSkills: [], missingDesiredSkills: [], summary: 'x', scoreType: 'STRUCTURED', structuredScore: 80, semanticScore: null,
}

describe('describeMatch', () => {
  it('treats a missing match as unavailable, never as zero', () => {
    expect(describeMatch(null).state).toBe('unavailable')
    expect(describeMatch(undefined).state).toBe('unavailable')
  })

  it('keeps a real zero as zero, with its own low-affinity text', () => {
    const view = describeMatch({ ...base, score: 0, structuredScore: 0 })
    expect(view).toMatchObject({ state: 'ready', score: 0, bandLabel: 'Baixa afinidade' })
  })

  it.each([[39, 'Baixa afinidade'], [40, 'Afinidade média'], [69, 'Afinidade média'], [70, 'Alta afinidade'], [100, 'Alta afinidade']])(
    'band for %i is %s', (score, label) => {
      expect(describeMatch({ ...base, score })).toMatchObject({ bandLabel: label })
    })

  it('labels each score type honestly', () => {
    expect(describeMatch(base)).toMatchObject({ typeLabel: 'Cálculo estruturado', semantic: null })
    expect(describeMatch({ ...base, scoreType: 'HYBRID', score: 98, structuredScore: 100, semanticScore: 89 })).toMatchObject({
      typeLabel: 'Estruturado e semântico', structured: 100, semantic: 89,
    })
    const fallback = describeMatch({ ...base, scoreType: 'FALLBACK' })
    expect(fallback).toMatchObject({ typeLabel: 'Cálculo estruturado (semântica indisponível)' })
    expect((fallback as { typeNote: string }).typeNote).toMatch(/indisponível/)
  })
})
