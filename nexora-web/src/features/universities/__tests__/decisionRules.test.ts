import { describe, expect, it } from 'vitest'
import { availableActions, validateReason, REASON_MAX } from '../decisionRules'

describe('availableActions (mirrors the backend rules)', () => {
  it('only PENDING can be approved or rejected', () => {
    expect(availableActions('PENDING')).toEqual(['APPROVE', 'REJECT'])
  })
  it('only APPROVED can be revoked', () => {
    expect(availableActions('APPROVED')).toEqual(['REVOKE'])
  })
  it.each(['REJECTED', 'REVOKED'] as const)('%s is history: no action', (status) => {
    expect(availableActions(status)).toEqual([])
  })
})

describe('validateReason', () => {
  it('is required to reject and to revoke', () => {
    expect(validateReason('REJECT', '')).toMatch(/motivo/i)
    expect(validateReason('REJECT', '   ')).toMatch(/motivo/i)
    expect(validateReason('REVOKE', '')).toMatch(/motivo/i)
    expect(validateReason('REJECT', 'Matrícula não localizada')).toBeNull()
    expect(validateReason('REVOKE', 'Trancamento')).toBeNull()
  })
  it('is optional to approve', () => {
    expect(validateReason('APPROVE', '')).toBeNull()
  })
  it('is limited to 500 characters for every decision', () => {
    expect(REASON_MAX).toBe(500)
    for (const kind of ['APPROVE', 'REJECT', 'REVOKE'] as const) {
      expect(validateReason(kind, 'x'.repeat(500))).toBeNull()
      expect(validateReason(kind, 'x'.repeat(501))).toMatch(/500/)
    }
  })
})
