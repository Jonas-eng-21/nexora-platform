import { http } from '@/shared/api/http'
import type { Page } from '@/features/students/types'
import type { StudentSummary } from '@/features/matching/api/companyMatchingApi'
import type { ApplicationStatus, ResumeType } from '../types'
import type { DecisionStatus } from '../statusRules'

export interface JobApplication {
  id: string
  status: ApplicationStatus
  resumeType: ResumeType
  appliedAt: string
  updatedAt: string
  student: StudentSummary
  /** Compatibilidade ATUAL (0 a 100); nulo quando o currículo-base não tem competências nem áreas. */
  matchScore: number | null
}
export interface StatusChange {
  id: string
  previousStatus: ApplicationStatus
  status: ApplicationStatus
  reason: string | null
  updatedAt: string
}
export interface Reactivation {
  id: string
  previousStatus: ApplicationStatus
  status: ApplicationStatus
  updatedAt: string
}

export const companyApplicationApi = {
  listForJob: (jobId: string, status: ApplicationStatus | '', page: number, size = 10) =>
    http
      .get<Page<JobApplication>>(`/jobs/${jobId}/applications`, { params: { status: status || undefined, page, size } })
      .then((r) => r.data),
  changeStatus: (applicationId: string, status: DecisionStatus, reason?: string) =>
    http.patch<StatusChange>(`/applications/${applicationId}/status`, { status, reason: reason?.trim() || undefined }).then((r) => r.data),
  reactivate: (applicationId: string) => http.post<Reactivation>(`/applications/${applicationId}/reactivate`).then((r) => r.data),
}
