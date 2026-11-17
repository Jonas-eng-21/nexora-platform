import type { Links, ProfessionalArea, Resume, StudentProfile, StudentProfileRequest, WorkModel } from './types'

export type FieldErrors = Record<string, string>

/** Estado de edição: todo valor é string, como nos inputs; a conversão para o contrato fica aqui. */
export interface ResumeFormState {
  links: { github: string; linkedin: string; portfolio: string }
  projects: { name: string; description: string; url: string }[]
  education: { institution: string; course: string; degree: string; startYear: string; endYear: string }[]
  experiences: { company: string; role: string; description: string; startDate: string; endDate: string }[]
  courses: { name: string; institution: string; year: string; certificateUrl: string }[]
  softSkills: string[]
  interests: string[]
  interestAreas: ProfessionalArea[]
  preferredWorkModels: WorkModel[]
}

/** Limites do backend (Resume.java). */
export const RESUME_LIMITS = { education: 10, experiences: 20, projects: 20, courses: 30, tags: 30 } as const

export const emptyResumeForm = (): ResumeFormState => ({
  links: { github: '', linkedin: '', portfolio: '' },
  projects: [], education: [], experiences: [], courses: [],
  softSkills: [], interests: [], interestAreas: [], preferredWorkModels: [],
})

const text = (v: string | number | null | undefined) => (v === null || v === undefined ? '' : String(v))

export function resumeToForm(resume: Resume): ResumeFormState {
  return {
    links: { github: text(resume.links?.github), linkedin: text(resume.links?.linkedin), portfolio: text(resume.links?.portfolio) },
    projects: resume.projects.map((p) => ({ name: p.name, description: text(p.description), url: text(p.url) })),
    education: resume.education.map((e) => ({
      institution: e.institution, course: e.course, degree: text(e.degree), startYear: text(e.startYear), endYear: text(e.endYear),
    })),
    experiences: resume.experiences.map((e) => ({
      company: e.company, role: e.role, description: text(e.description), startDate: e.startDate, endDate: text(e.endDate),
    })),
    courses: resume.courses.map((c) => ({ name: c.name, institution: text(c.institution), year: text(c.year), certificateUrl: text(c.certificateUrl) })),
    softSkills: [...resume.softSkills],
    interests: [...resume.interests],
    interestAreas: [...resume.interestAreas],
    preferredWorkModels: [...resume.preferredWorkModels],
  }
}

/** Campos opcionais vazios são omitidos: o PUT substitui o documento inteiro. */
const opt = (v: string): string | undefined => v.trim() || undefined
const optNum = (v: string): number | undefined => (v.trim() ? Number(v) : undefined)
const compact = <T extends object>(o: T): T => Object.fromEntries(Object.entries(o).filter(([, v]) => v !== undefined)) as T

export function formToResume(form: ResumeFormState): Resume {
  const links: Links = compact({ github: opt(form.links.github), linkedin: opt(form.links.linkedin), portfolio: opt(form.links.portfolio) })
  return {
    education: form.education.map((e) => compact({
      institution: e.institution.trim(), course: e.course.trim(), degree: opt(e.degree), startYear: Number(e.startYear), endYear: optNum(e.endYear),
    })) as Resume['education'],
    experiences: form.experiences.map((e) => compact({
      company: e.company.trim(), role: e.role.trim(), description: opt(e.description), startDate: e.startDate.trim(), endDate: opt(e.endDate),
    })) as Resume['experiences'],
    projects: form.projects.map((p) => compact({ name: p.name.trim(), description: opt(p.description), url: opt(p.url) })) as Resume['projects'],
    courses: form.courses.map((c) => compact({ name: c.name.trim(), institution: opt(c.institution), year: optNum(c.year), certificateUrl: opt(c.certificateUrl) })) as Resume['courses'],
    softSkills: form.softSkills, interests: form.interests, links,
    interestAreas: form.interestAreas, preferredWorkModels: form.preferredWorkModels,
  }
}

const URL_RE = /^https?:\/\/\S+$/
const MONTH_RE = /^\d{4}-(0[1-9]|1[0-2])$/
const URL_MSG = 'Informe uma URL começando com http:// ou https://.'
const MONTH_MSG = 'Use o formato AAAA-MM, por exemplo 2025-03.'

function year(value: string, errors: FieldErrors, key: string, required: boolean) {
  if (!value.trim()) {
    if (required) errors[key] = 'Informe o ano.'
    return
  }
  const n = Number(value)
  if (!Number.isInteger(n) || n < 1950 || n > 2100) errors[key] = 'Informe um ano entre 1950 e 2100.'
}
function max(value: string, limit: number, errors: FieldErrors, key: string) {
  if (value.length > limit) errors[key] = `Use até ${limit} caracteres.`
}
function url(value: string, errors: FieldErrors, key: string) {
  const v = value.trim()
  if (!v) return
  if (!URL_RE.test(v)) errors[key] = URL_MSG
  else max(v, 300, errors, key)
}

/** Espelha as regras do backend (caminhos iguais aos de `errors[].field`); a validação real é do servidor. */
export function validateResumeForm(form: ResumeFormState): FieldErrors {
  const errors: FieldErrors = {}
  url(form.links.github, errors, 'links.github')
  url(form.links.linkedin, errors, 'links.linkedin')
  url(form.links.portfolio, errors, 'links.portfolio')

  form.projects.forEach((p, i) => {
    const k = `projects[${i}]`
    if (!p.name.trim()) errors[`${k}.name`] = 'Informe o nome do projeto.'
    else max(p.name, 150, errors, `${k}.name`)
    max(p.description, 2000, errors, `${k}.description`)
    url(p.url, errors, `${k}.url`)
  })
  form.education.forEach((e, i) => {
    const k = `education[${i}]`
    if (!e.institution.trim()) errors[`${k}.institution`] = 'Informe a instituição.'
    else max(e.institution, 150, errors, `${k}.institution`)
    if (!e.course.trim()) errors[`${k}.course`] = 'Informe o curso.'
    else max(e.course, 150, errors, `${k}.course`)
    max(e.degree, 80, errors, `${k}.degree`)
    year(e.startYear, errors, `${k}.startYear`, true)
    year(e.endYear, errors, `${k}.endYear`, false)
  })
  form.experiences.forEach((e, i) => {
    const k = `experiences[${i}]`
    if (!e.company.trim()) errors[`${k}.company`] = 'Informe a empresa.'
    else max(e.company, 150, errors, `${k}.company`)
    if (!e.role.trim()) errors[`${k}.role`] = 'Informe o cargo.'
    else max(e.role, 150, errors, `${k}.role`)
    max(e.description, 2000, errors, `${k}.description`)
    if (!e.startDate.trim()) errors[`${k}.startDate`] = 'Informe o início no formato AAAA-MM.'
    else if (!MONTH_RE.test(e.startDate.trim())) errors[`${k}.startDate`] = MONTH_MSG
    if (e.endDate.trim() && !MONTH_RE.test(e.endDate.trim())) errors[`${k}.endDate`] = MONTH_MSG
  })
  form.courses.forEach((c, i) => {
    const k = `courses[${i}]`
    if (!c.name.trim()) errors[`${k}.name`] = 'Informe o nome do curso.'
    else max(c.name, 150, errors, `${k}.name`)
    max(c.institution, 150, errors, `${k}.institution`)
    year(c.year, errors, `${k}.year`, false)
    url(c.certificateUrl, errors, `${k}.certificateUrl`)
  })
  return errors
}

export interface ProfileFormState {
  fullName: string
  course: string
  institution: string
  location: string
  summary: string
}

export const emptyProfileForm = (): ProfileFormState => ({ fullName: '', course: '', institution: '', location: '', summary: '' })
export const profileToForm = (p: StudentProfile): ProfileFormState => ({
  fullName: p.fullName, course: text(p.course), institution: text(p.institution), location: text(p.location), summary: text(p.summary),
})
export const formToProfile = (f: ProfileFormState): StudentProfileRequest =>
  compact({ fullName: f.fullName.trim(), course: opt(f.course), institution: opt(f.institution), location: opt(f.location), summary: opt(f.summary) }) as StudentProfileRequest

export function validateProfile(form: ProfileFormState): FieldErrors {
  const errors: FieldErrors = {}
  if (!form.fullName.trim()) errors.fullName = 'Informe seu nome completo.'
  else max(form.fullName, 150, errors, 'fullName')
  max(form.course, 150, errors, 'course')
  max(form.institution, 150, errors, 'institution')
  max(form.location, 150, errors, 'location')
  max(form.summary, 2000, errors, 'summary')
  return errors
}
