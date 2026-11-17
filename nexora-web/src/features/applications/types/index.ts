import type { Resume } from '@/features/students/types'

export type ResumeType = 'PLATFORM' | 'PDF'
export type ApplicationStatus = 'SUBMITTED' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'SUSPENDED'

/** POST /jobs/{id}/applications */
export interface ApplicationCreated {
  id: string
  jobId: string
  jobTitle: string
  companyName: string
  resumeType: ResumeType
  resumeFileName: string | null
  resumeFileSize: number | null
  status: ApplicationStatus
  createdAt: string
}

/** GET /applications/me */
export interface ApplicationSummary {
  id: string
  jobId: string
  jobTitle: string
  companyName: string
  status: ApplicationStatus
  resumeType: ResumeType
  appliedAt: string
  updatedAt: string
}

/** GET /applications/{id}/resume quando PLATFORM. */
export interface ResumeSnapshot {
  profile: {
    fullName: string
    course: string | null
    institution: string | null
    location: string | null
    summary: string | null
  }
  resume: Resume
  skills: { id: string; name: string }[]
}
