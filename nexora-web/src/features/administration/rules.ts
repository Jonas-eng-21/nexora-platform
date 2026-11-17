import type { AccountStatus, UserRole } from '@/features/authentication/types'
import type { CompanyFormState } from '@/features/companies/companyForm'
import type { UniversityApprovalStatus } from './types'

export const REASON_MAX = 500
export type UserAction = 'SUSPEND' | 'REACTIVATE'

/** Espelha AdminUserService só para esconder ações inválidas; o backend continua a autoridade (409). */
export function userActions(user: { id: string; status: AccountStatus }, selfId: string | null): UserAction[] {
  if (user.status === 'ACTIVE') return user.id === selfId ? [] : ['SUSPEND']
  if (user.status === 'SUSPENDED') return ['REACTIVATE']
  return [] // PENDING_APPROVAL e REJECTED: universidades têm fluxo próprio de aprovação
}

export const universityActions = (status: UniversityApprovalStatus): ('APPROVE' | 'REJECT')[] =>
  status === 'PENDING' ? ['APPROVE', 'REJECT'] : []

export function suspensionConsequence(role: UserRole): string {
  if (role === 'UNIVERSITY') {
    return 'A suspensão bloqueia todas as operações da universidade e suspende automaticamente as candidaturas em andamento dos estudantes vinculados a ela. Reativar a conta depois não reativa essas candidaturas: cada uma exige reativação explícita pela empresa ou pelo administrador.'
  }
  return 'A suspensão bloqueia todas as operações da conta, mesmo com a sessão atual ainda aberta. A ação fica registrada na auditoria.'
}

/** Nas intervenções administrativas o motivo é sempre obrigatório (vai para a auditoria). */
export function validateAdminReason(reason: string): string | null {
  if (!reason.trim()) return 'Informe o motivo da intervenção.'
  if (reason.length > REASON_MAX) return `O motivo pode ter até ${REASON_MAX} caracteres.`
  return null
}

export function adminCompanyFormToRequest(form: CompanyFormState, reason: string) {
  const profile: { name: string; sector?: string; location?: string; description?: string } = { name: form.name.trim() }
  if (form.sector.trim()) profile.sector = form.sector.trim()
  if (form.location.trim()) profile.location = form.location.trim()
  if (form.description.trim()) profile.description = form.description.trim()
  return { profile, reason: reason.trim() }
}
