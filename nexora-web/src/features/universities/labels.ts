import type { AffiliationStatus } from './types'

export const AFFILIATION_STATUS_LABEL: Record<AffiliationStatus, string> = {
  PENDING: 'Aguardando análise', APPROVED: 'Aprovado', REJECTED: 'Rejeitado', REVOKED: 'Revogado',
}
export const AFFILIATION_STATUS_TONE: Record<AffiliationStatus, 'success' | 'warning' | 'danger'> = {
  PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', REVOKED: 'danger',
}
