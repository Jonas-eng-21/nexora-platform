import type { AccountStatus } from '@/features/authentication/types'
import type { AdminAction, AuditTargetType, UniversityApprovalStatus } from './types'

export const APPROVAL_LABEL: Record<UniversityApprovalStatus, string> = { PENDING: 'Aguardando aprovação', APPROVED: 'Aprovada', REJECTED: 'Rejeitada' }
export const APPROVAL_TONE: Record<UniversityApprovalStatus, 'success' | 'warning' | 'danger'> = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }

export const ACCOUNT_STATUS_LABEL: Record<AccountStatus, string> = {
  ACTIVE: 'Ativa', PENDING_APPROVAL: 'Aguardando aprovação', SUSPENDED: 'Suspensa', REJECTED: 'Rejeitada',
}
export const ACCOUNT_STATUS_TONE: Record<AccountStatus, 'success' | 'warning' | 'danger'> = {
  ACTIVE: 'success', PENDING_APPROVAL: 'warning', SUSPENDED: 'warning', REJECTED: 'danger',
}

export const ACTION_LABEL: Record<AdminAction, string> = {
  USER_SUSPENDED: 'Conta suspensa', USER_REACTIVATED: 'Conta reativada', COMPANY_PROFILE_UPDATED: 'Perfil de empresa alterado',
  UNIVERSITY_APPROVED: 'Universidade aprovada', UNIVERSITY_REJECTED: 'Universidade rejeitada',
  AFFILIATION_APPROVED: 'Vínculo aprovado', AFFILIATION_REJECTED: 'Vínculo rejeitado', AFFILIATION_REVOKED: 'Vínculo revogado',
  JOB_UPDATED: 'Vaga alterada', JOB_CLOSED: 'Vaga encerrada', JOB_REOPENED: 'Vaga reaberta', JOB_DELETED: 'Vaga excluída',
  APPLICATION_STATUS_CHANGED: 'Situação de candidatura alterada', APPLICATION_REACTIVATED: 'Candidatura reativada',
}
export const TARGET_LABEL: Record<AuditTargetType, string> = {
  USER: 'Conta', COMPANY: 'Empresa', UNIVERSITY: 'Universidade', AFFILIATION: 'Vínculo', JOB: 'Vaga', APPLICATION: 'Candidatura',
}
