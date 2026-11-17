import { describe, expect, it } from 'vitest'
import { emptyResumeForm, resumeToForm, formToResume, validateResumeForm, validateProfile } from '../resumeForm'
import type { Resume } from '../types'

const sample: Resume = {
  education: [{ institution: 'UFAPE', course: 'Sistemas', degree: null, startYear: 2023, endYear: null }],
  experiences: [],
  projects: [{ name: 'Nexora', description: 'TCC', url: 'https://github.com/x/nexora' }],
  courses: [],
  softSkills: ['Comunicação'],
  interests: [],
  links: { github: 'https://github.com/x', linkedin: null, portfolio: null },
  interestAreas: ['BACKEND'],
  preferredWorkModels: ['REMOTE'],
}

describe('resume form mapping', () => {
  it('turns nulls into empty strings and numbers into strings for editing', () => {
    const form = resumeToForm(sample)
    expect(form.education[0]).toEqual({ institution: 'UFAPE', course: 'Sistemas', degree: '', startYear: '2023', endYear: '' })
    expect(form.links.linkedin).toBe('')
  })

  it('round-trips without losing data and omits empty optionals', () => {
    const request = formToResume(resumeToForm(sample))
    expect(request.education[0]).toEqual({ institution: 'UFAPE', course: 'Sistemas', startYear: 2023 })
    expect(request.projects[0]).toEqual({ name: 'Nexora', description: 'TCC', url: 'https://github.com/x/nexora' })
    expect(request.links).toEqual({ github: 'https://github.com/x' })
    expect(request.interestAreas).toEqual(['BACKEND'])
    expect(request.preferredWorkModels).toEqual(['REMOTE'])
  })

  it('trims text values', () => {
    const form = emptyResumeForm()
    form.projects.push({ name: '  API  ', description: '', url: '' })
    expect(formToResume(form).projects[0]).toEqual({ name: 'API' })
  })
})

describe('validateResumeForm', () => {
  it('accepts the empty resume (all sections are optional)', () => {
    expect(validateResumeForm(emptyResumeForm())).toEqual({})
  })

  it('reports errors with the same field paths the API uses', () => {
    const form = emptyResumeForm()
    form.projects.push({ name: '', description: '', url: 'ftp://x' })
    form.education.push({ institution: 'UFAPE', course: '', degree: '', startYear: '1900', endYear: '' })
    form.experiences.push({ company: 'ACME', role: 'Dev', description: '', startDate: '03/2025', endDate: '' })
    form.courses.push({ name: 'Curso', institution: '', year: '3000', certificateUrl: '' })
    form.links.github = 'github.com/x'
    const errors = validateResumeForm(form)
    expect(errors['projects[0].name']).toBeTruthy()
    expect(errors['projects[0].url']).toBeTruthy()
    expect(errors['education[0].course']).toBeTruthy()
    expect(errors['education[0].startYear']).toBeTruthy()
    expect(errors['experiences[0].startDate']).toMatch(/AAAA-MM/)
    expect(errors['courses[0].year']).toBeTruthy()
    expect(errors['links.github']).toBeTruthy()
  })

  it('accepts a valid month and an optional end date', () => {
    const form = emptyResumeForm()
    form.experiences.push({ company: 'ACME', role: 'Dev', description: '', startDate: '2025-03', endDate: '2025-13' })
    expect(validateResumeForm(form)['experiences[0].endDate']).toBeTruthy()
    form.experiences[0]!.endDate = '2025-12'
    expect(validateResumeForm(form)).toEqual({})
  })
})

describe('validateProfile', () => {
  it('requires the full name and limits lengths', () => {
    expect(validateProfile({ fullName: ' ', course: '', institution: '', location: '', summary: '' }).fullName).toBeTruthy()
    expect(validateProfile({ fullName: 'Ana', course: 'x'.repeat(151), institution: '', location: '', summary: '' }).course).toBeTruthy()
    expect(validateProfile({ fullName: 'Ana', course: '', institution: '', location: '', summary: 'x'.repeat(2001) }).summary).toBeTruthy()
    expect(validateProfile({ fullName: 'Ana', course: '', institution: '', location: '', summary: '' })).toEqual({})
  })
})
