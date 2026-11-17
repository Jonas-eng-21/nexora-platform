import { http } from '@/shared/api/http'
import type { Page, ProfessionalArea } from '@/features/students/types'
import type { MatchResult } from '../types'

/** Dados do estudante autorizados para a empresa: sem e-mail, instituição, links nem currículo. */
export interface StudentSummary {
  id: string
  fullName: string
  course: string | null
  location: string | null
  summary: string | null
  skills: string[]
  interestAreas: ProfessionalArea[]
}
export interface RecommendedStudent {
  student: StudentSummary
  match: MatchResult
}

export const companyMatchingApi = {
  students: (jobId: string, page: number, size = 10) =>
    http.get<Page<RecommendedStudent>>(`/matching/jobs/${jobId}/students`, { params: { page, size } }).then((r) => r.data),
}
