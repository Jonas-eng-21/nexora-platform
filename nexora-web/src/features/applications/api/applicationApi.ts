import { http } from '@/shared/api/http'
import type { Page } from '@/features/students/types'
import type { ApplicationCreated, ApplicationStatus, ApplicationSummary, ResumeSnapshot, ResumeType } from '../types'

export type SentResume = { kind: 'snapshot'; snapshot: ResumeSnapshot } | { kind: 'pdf'; blob: Blob; filename: string }

export const applicationApi = {
  /** multipart/form-data também para PLATFORM, como exige o backend. */
  apply(jobId: string, resumeType: ResumeType, file?: File) {
    const body = new FormData()
    body.append('resumeType', resumeType)
    if (resumeType === 'PDF' && file) body.append('file', file)
    return http.post<ApplicationCreated>(`/jobs/${jobId}/applications`, body).then((r) => r.data)
  },
  listOwn: (status: ApplicationStatus | '', page: number, size = 10) =>
    http
      .get<Page<ApplicationSummary>>('/applications/me', { params: { status: status || undefined, page, size } })
      .then((r) => r.data),
  async readResume(applicationId: string): Promise<SentResume> {
    const response = await http.get<Blob>(`/applications/${applicationId}/resume`, { responseType: 'blob' })
    const type = response.headers['content-type'] ?? response.data.type
    if (String(type).includes('json')) {
      return { kind: 'snapshot', snapshot: JSON.parse(await response.data.text()) as ResumeSnapshot }
    }
    const disposition = String(response.headers['content-disposition'] ?? '')
    const match = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
    return { kind: 'pdf', blob: response.data, filename: match ? decodeURIComponent(match[1]!) : 'curriculo.pdf' }
  },
}
