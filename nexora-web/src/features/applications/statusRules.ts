import type { ApplicationStatus } from './types'

export type DecisionStatus = 'IN_REVIEW' | 'APPROVED' | 'REJECTED'
export const REASON_MAX = 500

/** Espelha as transições do backend, só para esconder ações inválidas; o backend continua a autoridade (409). */
export function availableActions(status: ApplicationStatus): DecisionStatus[] {
  if (status === 'SUBMITTED') return ['IN_REVIEW', 'APPROVED', 'REJECTED']
  if (status === 'IN_REVIEW') return ['APPROVED', 'REJECTED']
  return [] // APPROVED e REJECTED são finais; SUSPENDED só avança pela reativação explícita
}

export const canReactivate = (status: ApplicationStatus): boolean => status === 'SUSPENDED'

export function validateDecision(target: DecisionStatus, reason: string): string | null {
  if (target === 'REJECTED' && !reason.trim()) return 'Informe o motivo da rejeição.'
  if (reason.length > REASON_MAX) return `O motivo pode ter até ${REASON_MAX} caracteres.`
  return null
}
