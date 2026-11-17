import { describe, expect, it } from 'vitest'
import { describeScore } from '../scoreDisplay'

describe('describeScore (matchScore of the applications list)', () => {
  it('null is "insufficient data", never zero', () => {
    expect(describeScore(null)).toEqual({ state: 'insufficient' })
    expect(describeScore(undefined)).toEqual({ state: 'insufficient' })
  })
  it('a real zero stays zero', () => {
    expect(describeScore(0)).toEqual({ state: 'ready', score: 0, bandLabel: 'Baixa afinidade' })
  })
  it.each([[39, 'Baixa afinidade'], [40, 'Afinidade média'], [69, 'Afinidade média'], [70, 'Alta afinidade']])('%i -> %s', (score, label) => {
    expect(describeScore(score)).toMatchObject({ state: 'ready', score, bandLabel: label })
  })
})
