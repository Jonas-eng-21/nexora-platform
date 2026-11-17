import { describe, expect, it } from 'vitest'
import { validateLogin, validateRegister } from '../validation'

describe('validateLogin', () => {
  it('accepts filled credentials', () => {
    expect(validateLogin({ email: 'ana@exemplo.com', password: 'x' })).toEqual({})
  })
  it('requires both fields', () => {
    const errors = validateLogin({ email: ' ', password: '' })
    expect(errors.email).toBeTruthy()
    expect(errors.password).toBeTruthy()
  })
})

describe('validateRegister', () => {
  const base = { email: 'ana@exemplo.com', password: 'senha-segura', role: 'STUDENT' as const }

  it('accepts a valid student', () => {
    expect(validateRegister({ ...base, university: { name: '' } })).toEqual({})
  })
  it('rejects a malformed e-mail', () => {
    expect(validateRegister({ ...base, email: 'sem-arroba', university: { name: '' } }).email).toBeTruthy()
  })
  it('rejects passwords under 8 characters', () => {
    expect(validateRegister({ ...base, password: '1234567', university: { name: '' } }).password).toBeTruthy()
  })
  it('rejects passwords over 72 bytes, counting accents as multiple bytes', () => {
    const accented = 'é'.repeat(37) // 74 bytes em UTF-8, 37 caracteres
    expect(validateRegister({ ...base, password: accented, university: { name: '' } }).password).toBeTruthy()
  })
  it('requires the institution name only for universities', () => {
    expect(validateRegister({ ...base, role: 'UNIVERSITY', university: { name: ' ' } })['university.name']).toBeTruthy()
    expect(validateRegister({ ...base, role: 'COMPANY', university: { name: '' } })['university.name']).toBeUndefined()
  })
  it('accepts only http(s) websites', () => {
    const uni = { ...base, role: 'UNIVERSITY' as const }
    expect(validateRegister({ ...uni, university: { name: 'UFAPE', website: 'ftp://x' } })['university.website']).toBeTruthy()
    expect(validateRegister({ ...uni, university: { name: 'UFAPE', website: 'https://ufape.edu.br' } })).toEqual({})
  })
})
