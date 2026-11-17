import { http } from '@/shared/api/http'
import type { Page } from '@/features/students/types'
import type { AccountStatus, UserAccount, UserRole } from '@/features/authentication/types'
import type { CompanyProfile } from '@/features/companies/types'
import type { Job } from '@/features/jobs/types'
import type { ReceivedAffiliation, AffiliationStatus } from '@/features/universities/types'
import type { JobApplication, Reactivation, StatusChange } from '@/features/applications/api/companyApplicationApi'
import type { ApplicationStatus } from '@/features/applications/types'
import type { DecisionStatus } from '@/features/applications/statusRules'
import type { AdminUniversity, AuditLog, UniversityApprovalStatus } from '../types'
import type { adminCompanyFormToRequest } from '../rules'

const page = (n: number, size: number) => ({ page: n, size })

export const adminApi = {
  // Universidades
  listUniversities: (approvalStatus: UniversityApprovalStatus | '', n: number, size = 10) =>
    http.get<Page<AdminUniversity>>('/admin/universities', { params: { approvalStatus: approvalStatus || undefined, ...page(n, size) } }).then((r) => r.data),
  getUniversity: (id: string) => http.get<AdminUniversity>(`/admin/universities/${id}`).then((r) => r.data),
  /** O corpo é obrigatório; a observação é opcional ao aprovar. */
  approveUniversity: (id: string, reason?: string) =>
    http.post<AdminUniversity>(`/admin/universities/${id}/approve`, { reason: reason?.trim() || undefined }).then((r) => r.data),
  rejectUniversity: (id: string, reason: string) => http.post<AdminUniversity>(`/admin/universities/${id}/reject`, { reason: reason.trim() }).then((r) => r.data),

  // Contas
  listUsers: (role: UserRole | '', status: AccountStatus | '', n: number, size = 10) =>
    http.get<Page<UserAccount>>('/admin/users', { params: { role: role || undefined, status: status || undefined, ...page(n, size) } }).then((r) => r.data),
  getUser: (id: string) => http.get<UserAccount>(`/admin/users/${id}`).then((r) => r.data),
  suspendUser: (id: string, reason: string) => http.post<UserAccount>(`/admin/users/${id}/suspend`, { reason: reason.trim() }).then((r) => r.data),
  reactivateUser: (id: string, reason: string) => http.post<UserAccount>(`/admin/users/${id}/reactivate`, { reason: reason.trim() }).then((r) => r.data),

  // Vínculos (todas as universidades)
  listAffiliations: (status: AffiliationStatus | '', n: number, size = 10) =>
    http.get<Page<ReceivedAffiliation>>('/admin/affiliations', { params: { status: status || undefined, ...page(n, size) } }).then((r) => r.data),
  approveAffiliation: (id: string, reason: string) => http.post<ReceivedAffiliation>(`/admin/affiliations/${id}/approve`, { reason: reason.trim() }).then((r) => r.data),
  rejectAffiliation: (id: string, reason: string) => http.post<ReceivedAffiliation>(`/admin/affiliations/${id}/reject`, { reason: reason.trim() }).then((r) => r.data),
  revokeAffiliation: (id: string, reason: string) => http.post<ReceivedAffiliation>(`/admin/affiliations/${id}/revoke`, { reason: reason.trim() }).then((r) => r.data),

  // Auditoria (somente leitura, só paginação)
  listAuditLogs: (n: number, size = 20) => http.get<Page<AuditLog>>('/admin/audit-logs', { params: page(n, size) }).then((r) => r.data),

  // Empresas e vagas (por identificador conhecido)
  getCompany: (id: string) => http.get<CompanyProfile>(`/admin/companies/${id}`).then((r) => r.data),
  updateCompany: (id: string, payload: ReturnType<typeof adminCompanyFormToRequest>) => http.put<CompanyProfile>(`/admin/companies/${id}`, payload).then((r) => r.data),
  closeJob: (id: string, reason: string) => http.post<Job>(`/admin/jobs/${id}/close`, { reason: reason.trim() }).then((r) => r.data),
  reopenJob: (id: string, reason: string) => http.post<Job>(`/admin/jobs/${id}/reopen`, { reason: reason.trim() }).then((r) => r.data),
  deleteJob: (id: string, reason: string) => http.delete(`/admin/jobs/${id}`, { data: { reason: reason.trim() } }).then(() => undefined),

  // Candidaturas: leitura por vaga e as mesmas decisões da empresa, com auditoria
  listJobApplications: (jobId: string, status: ApplicationStatus | '', n: number, size = 10) =>
    http.get<Page<JobApplication>>(`/admin/jobs/${jobId}/applications`, { params: { status: status || undefined, ...page(n, size) } }).then((r) => r.data),
  changeApplicationStatus: (id: string, status: DecisionStatus, reason?: string) =>
    http.patch<StatusChange>(`/admin/applications/${id}/status`, { status, reason: reason?.trim() || undefined }).then((r) => r.data),
  reactivateApplication: (id: string) => http.post<Reactivation>(`/admin/applications/${id}/reactivate`).then((r) => r.data),
}
