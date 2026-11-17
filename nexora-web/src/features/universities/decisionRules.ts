import type { AffiliationStatus } from './types'

export type DecisionKind = 'APPROVE' | 'REJECT' | 'REVOKE'
export const REASON_MAX = 500

/** Espelha as regras do backend só para esconder ações inválidas; o backend continua a autoridade (409). */
export function availableActions(status: AffiliationStatus): DecisionKind[] {
  if (status === 'PENDING') return ['APPROVE', 'REJECT']
  if (status === 'APPROVED') return ['REVOKE']
  return [] // REJECTED e REVOKED ficam como histórico
}

/** `strictApprove`: nas intervenções do administrador o backend exige motivo também para aprovar. */
export function validateReason(kind: DecisionKind, reason: string, strictApprove = false): string | null {
  if ((kind !== 'APPROVE' || strictApprove) && !reason.trim()) {
    return kind === 'REJECT' ? 'Informe o motivo da rejeição.' : kind === 'REVOKE' ? 'Informe o motivo da revogação.' : 'Informe o motivo da aprovação.'
  }
  if (reason.length > REASON_MAX) return `O motivo pode ter até ${REASON_MAX} caracteres.`
  return null
}
