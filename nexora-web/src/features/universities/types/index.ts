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

/** GET/PUT /universities/me */
export interface UniversityProfile {
  id: string
  name: string
  acronym: string | null
  location: string | null
  website: string | null
  description: string | null
}
export interface UniversityProfileRequest {
  name: string
  acronym?: string
  location?: string
  website?: string
  description?: string
}

/** Campos do estudante que o backend autoriza para a universidade (sem e-mail). */
export interface AffiliationStudent {
  id: string
  fullName: string
  course: string | null
  institution: string | null
}

/** GET /affiliations/received e respostas de approve, reject e revoke. */
export interface ReceivedAffiliation {
  id: string
  student: AffiliationStudent
  university: UniversitySummary
  status: AffiliationStatus
  requestedAt: string
  decidedAt: string | null
  decisionReason: string | null
  revokedAt: string | null
  revocationReason: string | null
}
