import { describe, expect, it } from 'vitest'
import { emptyUniversityForm, universityFormToRequest, universityToForm, validateUniversityForm } from '../universityForm'

const valid = () => ({ name: 'Universidade de Garanhuns', acronym: 'UG', location: 'Garanhuns - PE', website: 'https://ug.edu.br', description: 'Instituição de ensino.' })

describe('validateUniversityForm', () => {
  it('accepts a complete profile and one with only the name', () => {
    expect(validateUniversityForm(valid())).toEqual({})
    expect(validateUniversityForm({ ...emptyUniversityForm(), name: 'UG' })).toEqual({})
  })
  it('requires the name', () => {
    expect(validateUniversityForm({ ...valid(), name: '  ' }).name).toBeTruthy()
  })
  it('limits lengths like the backend (150 / 30 / 150 / 300 / 2000)', () => {
    const errors = validateUniversityForm({ name: 'x'.repeat(151), acronym: 'x'.repeat(31), location: 'x'.repeat(151), website: 'https://' + 'x'.repeat(300), description: 'x'.repeat(2001) })
    for (const key of ['name', 'acronym', 'location', 'website', 'description']) expect(errors[key]).toBeTruthy()
  })
  it('accepts only http(s) websites, and blank', () => {
    expect(validateUniversityForm({ ...valid(), website: 'ftp://x.edu' }).website).toBeTruthy()
    expect(validateUniversityForm({ ...valid(), website: 'ug.edu.br' }).website).toBeTruthy()
    expect(validateUniversityForm({ ...valid(), website: '' }).website).toBeUndefined()
  })
})

describe('mapping', () => {
  it('loads nulls as empty strings', () => {
    expect(universityToForm({ id: '1', name: 'UG', acronym: null, location: null, website: null, description: null })).toEqual({ name: 'UG', acronym: '', location: '', website: '', description: '' })
  })
  it('sends trimmed values and omits blanks (the PUT replaces the whole profile)', () => {
    expect(universityFormToRequest({ ...valid(), name: '  UG  ', acronym: ' ', location: '' })).toEqual({
      name: 'UG', website: 'https://ug.edu.br', description: 'Instituição de ensino.',
    })
  })
})
