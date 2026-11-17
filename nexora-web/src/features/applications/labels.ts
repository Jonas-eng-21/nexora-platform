import type { ApplicationStatus, ResumeType } from './types'

export const APPLICATION_STATUS_LABEL: Record<ApplicationStatus, string> = {
  SUBMITTED: 'Enviada', IN_REVIEW: 'Em análise', APPROVED: 'Aprovada', REJECTED: 'Rejeitada', SUSPENDED: 'Suspensa',
}
export const APPLICATION_STATUS_TONE: Record<ApplicationStatus, 'success' | 'warning' | 'danger' | 'neutral'> = {
  SUBMITTED: 'neutral', IN_REVIEW: 'neutral', APPROVED: 'success', REJECTED: 'danger', SUSPENDED: 'warning',
}
export const APPLICATION_STATUS_OPTIONS = (Object.keys(APPLICATION_STATUS_LABEL) as ApplicationStatus[]).map((value) => ({
  value, label: APPLICATION_STATUS_LABEL[value],
}))
export const RESUME_TYPE_LABEL: Record<ResumeType, string> = { PLATFORM: 'Currículo do Nexora', PDF: 'PDF' }
