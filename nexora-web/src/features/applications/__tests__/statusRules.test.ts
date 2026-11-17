import { describe, expect, it } from 'vitest'
import { availableActions, canReactivate, validateDecision, REASON_MAX } from '../statusRules'

describe('availableActions (mirrors the backend transitions)', () => {
  it('SUBMITTED can go to IN_REVIEW, APPROVED or REJECTED', () => {
    expect(availableActions('SUBMITTED')).toEqual(['IN_REVIEW', 'APPROVED', 'REJECTED'])
  })
  it('IN_REVIEW can go to APPROVED or REJECTED only', () => {
    expect(availableActions('IN_REVIEW')).toEqual(['APPROVED', 'REJECTED'])
  })
  it.each(['APPROVED', 'REJECTED'] as const)('%s is final: no action', (status) => {
    expect(availableActions(status)).toEqual([])
  })
  it('SUSPENDED has no status action: only explicit reactivation', () => {
    expect(availableActions('SUSPENDED')).toEqual([])
    expect(canReactivate('SUSPENDED')).toBe(true)
  })
  it('only a suspended application can be reactivated', () => {
    for (const s of ['SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED'] as const) expect(canReactivate(s)).toBe(false)
  })
})

describe('validateDecision', () => {
  it('requires a reason to reject', () => {
    expect(validateDecision('REJECTED', '')).toMatch(/motivo/i)
    expect(validateDecision('REJECTED', '   ')).toMatch(/motivo/i)
    expect(validateDecision('REJECTED', 'Perfil fora da vaga')).toBeNull()
  })
  it('makes the reason optional for the other decisions', () => {
    expect(validateDecision('APPROVED', '')).toBeNull()
    expect(validateDecision('IN_REVIEW', '')).toBeNull()
  })
  it('limits the reason to 500 characters for every decision', () => {
    expect(REASON_MAX).toBe(500)
    expect(validateDecision('REJECTED', 'x'.repeat(500))).toBeNull()
    expect(validateDecision('REJECTED', 'x'.repeat(501))).toMatch(/500/)
    expect(validateDecision('APPROVED', 'x'.repeat(501))).toMatch(/500/)
  })
})
