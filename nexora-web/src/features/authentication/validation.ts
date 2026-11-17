import type { PublicRole, UniversityProfileRequest } from './types'

export type FieldErrors = Record<string, string>

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const HTTP_URL = /^https?:\/\/\S+$/

const utf8Bytes = (value: string) => new TextEncoder().encode(value).length

/** Espelha as regras do backend apenas para dar retorno rápido; a validação real é do servidor. */
export function validateLogin(input: { email: string; password: string }): FieldErrors {
  const errors: FieldErrors = {}
  if (!input.email.trim()) errors.email = 'Informe seu e-mail.'
  if (!input.password) errors.password = 'Informe sua senha.'
  return errors
}

export function validateRegister(input: {
  email: string
  password: string
  role: PublicRole
  university: UniversityProfileRequest
}): FieldErrors {
  const errors: FieldErrors = {}
  const email = input.email.trim()

  if (!email) errors.email = 'Informe seu e-mail.'
  else if (!EMAIL.test(email)) errors.email = 'Informe um e-mail válido, como nome@exemplo.com.'
  else if (email.length > 254) errors.email = 'Use um e-mail com até 254 caracteres.'

  if (!input.password) errors.password = 'Crie uma senha.'
  else if (input.password.length < 8) errors.password = 'Use pelo menos 8 caracteres.'
  else if (utf8Bytes(input.password) > 72) errors.password = 'Use no máximo 72 bytes (acentos contam mais de um).'

  if (input.role === 'UNIVERSITY') {
    const uni = input.university
    if (!uni.name.trim()) errors['university.name'] = 'Informe o nome da instituição.'
    else if (uni.name.length > 150) errors['university.name'] = 'Use até 150 caracteres.'
    if ((uni.acronym ?? '').length > 30) errors['university.acronym'] = 'Use até 30 caracteres.'
    if ((uni.location ?? '').length > 150) errors['university.location'] = 'Use até 150 caracteres.'
    const website = (uni.website ?? '').trim()
    if (website && !HTTP_URL.test(website)) errors['university.website'] = 'Informe uma URL começando com http:// ou https://.'
    else if (website.length > 300) errors['university.website'] = 'Use até 300 caracteres.'
    if ((uni.description ?? '').length > 2000) errors['university.description'] = 'Use até 2000 caracteres.'
  }
  return errors
}
