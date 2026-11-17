import type { AccountStatus, UserRole } from './types'

export const ROLE_HOME: Record<UserRole, string> = {
  STUDENT: '/estudante',
  COMPANY: '/empresa',
  UNIVERSITY: '/universidade',
  ADMIN: '/admin',
}

export const ROLE_LABEL: Record<UserRole, string> = {
  STUDENT: 'Estudante',
  COMPANY: 'Empresa',
  UNIVERSITY: 'Universidade',
  ADMIN: 'Administrador',
}

export const STATUS_LABEL: Record<AccountStatus, string> = {
  ACTIVE: 'Ativa',
  PENDING_APPROVAL: 'Aguardando aprovação',
  SUSPENDED: 'Suspensa',
  REJECTED: 'Rejeitada',
}

export const STATUS_TONE: Record<AccountStatus, 'success' | 'warning' | 'danger'> = {
  ACTIVE: 'success',
  PENDING_APPROVAL: 'warning',
  SUSPENDED: 'danger',
  REJECTED: 'danger',
}
