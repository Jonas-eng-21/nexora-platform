import { http } from '@/shared/api/http'
import type { Page } from '@/features/students/types'
import type { Job } from '../types'
import type { JobRequest } from '../jobForm'

export const jobManagementApi = {
  listMine: (status: 'OPEN' | 'CLOSED' | '', page: number, size = 10) =>
    http.get<Page<Job>>('/jobs/mine', { params: { status: status || undefined, page, size } }).then((r) => r.data),
  get: (jobId: string) => http.get<Job>(`/jobs/${jobId}`).then((r) => r.data),
  create: (payload: JobRequest) => http.post<Job>('/jobs', payload).then((r) => r.data),
  update: (jobId: string, payload: JobRequest) => http.put<Job>(`/jobs/${jobId}`, payload).then((r) => r.data),
  close: (jobId: string) => http.post<Job>(`/jobs/${jobId}/close`).then((r) => r.data),
  reopen: (jobId: string) => http.post<Job>(`/jobs/${jobId}/reopen`).then((r) => r.data),
  remove: (jobId: string) => http.delete(`/jobs/${jobId}`).then(() => undefined),
}
