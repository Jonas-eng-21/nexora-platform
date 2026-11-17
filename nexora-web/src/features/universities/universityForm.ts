import type { UniversityProfile, UniversityProfileRequest } from './types'

export type UniversityFieldErrors = Record<string, string>
export interface UniversityFormState { name: string; acronym: string; location: string; website: string; description: string }

const HTTP_URL = /^https?:\/\/\S+$/

export const emptyUniversityForm = (): UniversityFormState => ({ name: '', acronym: '', location: '', website: '', description: '' })
export const universityToForm = (u: UniversityProfile): UniversityFormState => ({
  name: u.name, acronym: u.acronym ?? '', location: u.location ?? '', website: u.website ?? '', description: u.description ?? '',
})

export function universityFormToRequest(f: UniversityFormState): UniversityProfileRequest {
  const request: UniversityProfileRequest = { name: f.name.trim() }
  if (f.acronym.trim()) request.acronym = f.acronym.trim()
  if (f.location.trim()) request.location = f.location.trim()
  if (f.website.trim()) request.website = f.website.trim()
  if (f.description.trim()) request.description = f.description.trim()
  return request
}

export function validateUniversityForm(f: UniversityFormState): UniversityFieldErrors {
  const errors: UniversityFieldErrors = {}
  if (!f.name.trim()) errors.name = 'Informe o nome da instituição.'
  else if (f.name.length > 150) errors.name = 'Use até 150 caracteres.'
  if (f.acronym.length > 30) errors.acronym = 'Use até 30 caracteres.'
  if (f.location.length > 150) errors.location = 'Use até 150 caracteres.'
  const website = f.website.trim()
  if (website && !HTTP_URL.test(website)) errors.website = 'Informe uma URL começando com http:// ou https://.'
  else if (website.length > 300) errors.website = 'Use até 300 caracteres.'
  if (f.description.length > 2000) errors.description = 'Use até 2.000 caracteres.'
  return errors
}
