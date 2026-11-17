import type { Skill } from '@/features/students/types'
import type { Job } from './types'

export type FieldErrors = Record<string, string>
export const MAX_JOB_SKILLS = 30

export interface JobFormState {
  title: string
  description: string
  area: string
  location: string
  workModel: string
  level: string
  required: Skill[]
  desired: Skill[]
}

/** Corpo de POST/PUT /jobs (CreateJobRequest). `status` fica de fora: o PUT mantém o atual e a vaga nasce OPEN. */
export interface JobRequest {
  title: string
  description: string
  area: string
  location?: string
  workModel: string
  level: string
  requiredSkillIds: string[]
  desiredSkillIds: string[]
}

export const emptyJobForm = (): JobFormState => ({
  title: '', description: '', area: '', location: '', workModel: '', level: '', required: [], desired: [],
})

export const jobToForm = (job: Job): JobFormState => ({
  title: job.title, description: job.description, area: job.area, location: job.location ?? '',
  workModel: job.workModel, level: job.level, required: [...job.requiredSkills], desired: [...job.desiredSkills],
})

export function formToJobRequest(form: JobFormState): JobRequest {
  const request: JobRequest = {
    title: form.title.trim(), description: form.description.trim(), area: form.area, workModel: form.workModel, level: form.level,
    requiredSkillIds: form.required.map((s) => s.id), desiredSkillIds: form.desired.map((s) => s.id),
  }
  if (form.location.trim()) request.location = form.location.trim()
  return request
}

/** Mesmas regras do backend (CreateJobRequest + JOB_SKILL_CONFLICT) para retorno imediato. */
export function validateJobForm(form: JobFormState): FieldErrors {
  const errors: FieldErrors = {}
  if (!form.title.trim()) errors.title = 'Informe o título da vaga.'
  else if (form.title.length > 150) errors.title = 'Use até 150 caracteres.'
  if (!form.description.trim()) errors.description = 'Descreva a vaga.'
  else if (form.description.length > 5000) errors.description = 'Use até 5.000 caracteres.'
  if (form.location.length > 150) errors.location = 'Use até 150 caracteres.'
  if (!form.area) errors.area = 'Escolha a área.'
  if (!form.workModel) errors.workModel = 'Escolha o modelo de trabalho.'
  if (!form.level) errors.level = 'Escolha o nível.'

  if (form.required.length === 0) errors.required = 'Escolha ao menos uma competência obrigatória.'
  else if (form.required.length > MAX_JOB_SKILLS) errors.required = `Escolha até ${MAX_JOB_SKILLS} competências obrigatórias.`
  if (form.desired.length > MAX_JOB_SKILLS) errors.desired = `Escolha até ${MAX_JOB_SKILLS} competências desejáveis.`
  else {
    const requiredIds = new Set(form.required.map((s) => s.id))
    const clash = form.desired.find((s) => requiredIds.has(s.id))
    if (clash) errors.desired = `A competência “${clash.name}” não pode ser obrigatória e desejável ao mesmo tempo.`
  }
  return errors
}
