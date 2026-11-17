import { describe, expect, it } from 'vitest'
import { emptyJobForm, formToJobRequest, jobToForm, validateJobForm, MAX_JOB_SKILLS } from '../jobForm'
import type { Job } from '../types'

const skill = (id: string, name = id) => ({ id, name })
const valid = () => ({
  ...emptyJobForm(), title: 'Estágio Backend', description: 'Atuar com APIs.', area: 'BACKEND', location: 'Garanhuns - PE',
  workModel: 'HYBRID', level: 'INTERNSHIP', required: [skill('s1', 'Java')], desired: [skill('s2', 'Docker')],
})

describe('validateJobForm', () => {
  it('accepts a complete job', () => {
    expect(validateJobForm(valid())).toEqual({})
  })
  it('requires title, description, area, work model, level and at least one required skill', () => {
    const errors = validateJobForm(emptyJobForm())
    for (const key of ['title', 'description', 'area', 'workModel', 'level', 'required']) expect(errors[key]).toBeTruthy()
  })
  it('limits lengths like the backend (150 / 5000 / 150)', () => {
    const errors = validateJobForm({ ...valid(), title: 'x'.repeat(151), description: 'x'.repeat(5001), location: 'x'.repeat(151) })
    expect(errors.title).toMatch(/150/)
    expect(errors.description).toMatch(/5\.?000/)
    expect(errors.location).toMatch(/150/)
  })
  it('does not allow the same skill as required and desired', () => {
    const errors = validateJobForm({ ...valid(), desired: [skill('s1', 'Java')] })
    expect(errors.desired).toMatch(/Java/)
  })
  it('caps each skill list at 30', () => {
    const many = Array.from({ length: MAX_JOB_SKILLS + 1 }, (_, i) => skill(`s${i}`))
    expect(validateJobForm({ ...valid(), required: many }).required).toMatch(/30/)
    expect(validateJobForm({ ...valid(), desired: many.map((s) => skill(`d${s.id}`)) }).desired).toMatch(/30/)
  })
})

describe('request mapping', () => {
  it('sends ids only, trimmed text, and omits the status so edits keep it', () => {
    const request = formToJobRequest({ ...valid(), title: '  Estágio Backend  ', location: '  ' })
    expect(request).toEqual({
      title: 'Estágio Backend', description: 'Atuar com APIs.', area: 'BACKEND', workModel: 'HYBRID', level: 'INTERNSHIP',
      requiredSkillIds: ['s1'], desiredSkillIds: ['s2'],
    })
    expect('status' in request).toBe(false)
  })
  it('loads an existing job into the form', () => {
    const job = {
      id: 'j1', companyId: 'c', companyName: 'ACME', title: 'T', description: 'D', area: 'DATA', location: 'Recife', workModel: 'REMOTE', level: 'JUNIOR',
      status: 'CLOSED', requiredSkills: [skill('a', 'SQL')], desiredSkills: [], createdAt: '', updatedAt: '',
    } as Job
    expect(jobToForm(job)).toMatchObject({ title: 'T', area: 'DATA', workModel: 'REMOTE', level: 'JUNIOR', location: 'Recife', required: [skill('a', 'SQL')], desired: [] })
  })
})
