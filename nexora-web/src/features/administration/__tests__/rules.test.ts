import { describe, expect, it } from 'vitest'
import { adminCompanyFormToRequest, suspensionConsequence, universityActions, userActions, validateAdminReason, REASON_MAX } from '../rules'

describe('userActions (mirrors AdminUserService)', () => {
  it('an active account of another user can be suspended', () => {
    expect(userActions({ id: 'u1', status: 'ACTIVE' }, 'admin')).toEqual(['SUSPEND'])
  })
  it('the admin cannot suspend their own account', () => {
    expect(userActions({ id: 'admin', status: 'ACTIVE' }, 'admin')).toEqual([])
  })
  it('only a suspended account can be reactivated', () => {
    expect(userActions({ id: 'u1', status: 'SUSPENDED' }, 'admin')).toEqual(['REACTIVATE'])
  })
  it.each(['PENDING_APPROVAL', 'REJECTED'] as const)('%s has no account action (university approval has its own flow)', (status) => {
    expect(userActions({ id: 'u1', status }, 'admin')).toEqual([])
  })
})

describe('universityActions', () => {
  it('only a pending registration can be decided; approved and rejected ones are history', () => {
    expect(universityActions('PENDING')).toEqual(['APPROVE', 'REJECT'])
    expect(universityActions('APPROVED')).toEqual([])
    expect(universityActions('REJECTED')).toEqual([])
  })
})

describe('suspensionConsequence', () => {
  it('explains the university cascade and that reactivation does not restore applications', () => {
    const text = suspensionConsequence('UNIVERSITY')
    expect(text).toMatch(/suspende automaticamente as candidaturas em andamento/)
    expect(text).toMatch(/não reativa essas candidaturas/)
  })
  it('explains the generic block for the other roles', () => {
    for (const role of ['STUDENT', 'COMPANY', 'ADMIN'] as const) {
      expect(suspensionConsequence(role)).toMatch(/bloqueia/)
      expect(suspensionConsequence(role)).not.toMatch(/candidaturas/)
    }
  })
})

describe('validateAdminReason', () => {
  it('is always required, trimmed, and limited to 500 characters', () => {
    expect(REASON_MAX).toBe(500)
    expect(validateAdminReason('')).toMatch(/motivo/i)
    expect(validateAdminReason('   ')).toMatch(/motivo/i)
    expect(validateAdminReason('x'.repeat(500))).toBeNull()
    expect(validateAdminReason('x'.repeat(501))).toMatch(/500/)
  })
})

describe('adminCompanyFormToRequest', () => {
  it('wraps the profile with the reason and omits blank optionals', () => {
    expect(adminCompanyFormToRequest({ name: ' Oficina ', sector: '', location: 'Garanhuns - PE', description: '' }, ' Dados corrigidos ')).toEqual({
      profile: { name: 'Oficina', location: 'Garanhuns - PE' }, reason: 'Dados corrigidos',
    })
  })
})
