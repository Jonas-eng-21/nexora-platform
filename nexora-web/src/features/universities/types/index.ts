export type AffiliationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED'

export interface UniversitySummary {
  id: string
  name: string
  acronym: string | null
  location: string | null
  website: string | null
}

export interface Affiliation {
  id: string
  university: UniversitySummary
  status: AffiliationStatus
  requestedAt: string
  decidedAt: string | null
  decisionReason: string | null
  revokedAt: string | null
  revocationReason: string | null
}
