import { http } from '@/shared/api/http'
import type { Page } from '@/features/students/types'
import type { JobMatch, Job, JobFilters, RecommendedJob } from '../types'

export const jobApi = {
  search: (filters: JobFilters, page: number, size = 10) =>
    http
      .get<Page<Job>>('/jobs', {
        params: {
          search: filters.search || undefined,
          area: filters.area || undefined,
          workModel: filters.workModel || undefined,
          level: filters.level || undefined,
          location: filters.location || undefined,
          page,
          size,
        },
      })
      .then((r) => r.data),
  get: (jobId: string) => http.get<Job>(`/jobs/${jobId}`).then((r) => r.data),
  recommended: (page: number, size = 10) =>
    http.get<Page<RecommendedJob>>('/matching/jobs', { params: { page, size } }).then((r) => r.data),
  match: (jobId: string) => http.get<JobMatch>(`/matching/jobs/${jobId}`).then((r) => r.data),
}
