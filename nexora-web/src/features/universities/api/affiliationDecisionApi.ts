import { http } from '@/shared/api/http'
import type { Page } from '@/features/students/types'
import type { AffiliationStatus, ReceivedAffiliation } from '../types'

// Lado da universidade autenticada; o catálogo e os pedidos do estudante ficam em affiliationApi.
export const affiliationDecisionApi = {
  listReceived: (status: AffiliationStatus | '', page: number, size = 10) =>
    http.get<Page<ReceivedAffiliation>>('/affiliations/received', { params: { status: status || undefined, page, size } }).then((r) => r.data),
  /** O corpo é obrigatório no backend; o motivo é opcional ao aprovar. */
  approve: (id: string, reason?: string) =>
    http.post<ReceivedAffiliation>(`/affiliations/${id}/approve`, { reason: reason?.trim() || undefined }).then((r) => r.data),
  reject: (id: string, reason: string) => http.post<ReceivedAffiliation>(`/affiliations/${id}/reject`, { reason: reason.trim() }).then((r) => r.data),
  revoke: (id: string, reason: string) => http.post<ReceivedAffiliation>(`/affiliations/${id}/revoke`, { reason: reason.trim() }).then((r) => r.data),
}
