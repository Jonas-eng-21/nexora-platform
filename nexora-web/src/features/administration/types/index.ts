import type { AccountStatus, UserRole } from '@/features/authentication/types'

export type UniversityApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** GET /admin/universities (e /{id}). O admin vê o e-mail da conta. */
export interface AdminUniversity {
  id: string
  userId: string
  email: string
  accountStatus: AccountStatus
  name: string
  acronym: string | null
  location: string | null
  website: string | null
  description: string | null
  approvalStatus: UniversityApprovalStatus
  approvalReason: string | null
  approvalDecidedBy: string | null
  approvalDecidedAt: string | null
  createdAt: string
}

export type AdminAction =
  | 'USER_SUSPENDED' | 'USER_REACTIVATED' | 'COMPANY_PROFILE_UPDATED' | 'UNIVERSITY_APPROVED' | 'UNIVERSITY_REJECTED'
  | 'AFFILIATION_APPROVED' | 'AFFILIATION_REJECTED' | 'AFFILIATION_REVOKED' | 'JOB_UPDATED' | 'JOB_CLOSED' | 'JOB_REOPENED'
  | 'JOB_DELETED' | 'APPLICATION_STATUS_CHANGED' | 'APPLICATION_REACTIVATED'
export type AuditTargetType = 'USER' | 'COMPANY' | 'UNIVERSITY' | 'AFFILIATION' | 'JOB' | 'APPLICATION'

/** GET /admin/audit-logs: só o que o backend devolve. */
export interface AuditLog {
  id: string
  adminId: string
  action: AdminAction
  targetType: AuditTargetType
  targetId: string
  reason: string | null
  previousValue: string | null
  newValue: string | null
  createdAt: string
}

export type { AccountStatus, UserRole }
