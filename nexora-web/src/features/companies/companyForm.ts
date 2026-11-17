import type { CompanyProfile, CompanyProfileRequest } from './types'

export type CompanyFieldErrors = Record<string, string>
export interface CompanyFormState { name: string; sector: string; location: string; description: string }

export const emptyCompanyForm = (): CompanyFormState => ({ name: '', sector: '', location: '', description: '' })
export const companyToForm = (c: CompanyProfile): CompanyFormState => ({
  name: c.name, sector: c.sector ?? '', location: c.location ?? '', description: c.description ?? '',
})
const opt = (v: string) => v.trim() || undefined
export const companyFormToRequest = (f: CompanyFormState): CompanyProfileRequest => {
  const request: CompanyProfileRequest = { name: f.name.trim() }
  if (opt(f.sector)) request.sector = f.sector.trim()
  if (opt(f.location)) request.location = f.location.trim()
  if (opt(f.description)) request.description = f.description.trim()
  return request
}

export function validateCompanyForm(f: CompanyFormState): CompanyFieldErrors {
  const errors: CompanyFieldErrors = {}
  if (!f.name.trim()) errors.name = 'Informe o nome da empresa.'
  else if (f.name.length > 150) errors.name = 'Use até 150 caracteres.'
  if (f.sector.length > 150) errors.sector = 'Use até 150 caracteres.'
  if (f.location.length > 150) errors.location = 'Use até 150 caracteres.'
  if (f.description.length > 2000) errors.description = 'Use até 2.000 caracteres.'
  return errors
}
