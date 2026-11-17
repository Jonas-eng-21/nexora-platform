import { http } from '@/shared/api/http'
import type { Page } from '@/features/students/types'
import type { Affiliation, UniversitySummary } from '../types'

export const affiliationApi = {
  listUniversities: () =>
    http.get<Page<UniversitySummary>>('/universities', { params: { page: 0, size: 100 } }).then((r) => r.data),
  request: (universityId: string) => http.post<Affiliation>('/affiliations', { universityId }).then((r) => r.data),
  listOwn: () => http.get<Affiliation[]>('/affiliations/me').then((r) => r.data),
  eligibility: () => http.get<{ eligible: boolean }>('/affiliations/me/eligibility').then((r) => r.data),
}
