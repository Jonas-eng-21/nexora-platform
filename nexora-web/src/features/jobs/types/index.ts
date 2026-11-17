import type { ProfessionalArea, Skill, WorkModel } from '@/features/students/types'
import type { MatchResult } from '@/features/matching/types'

export type JobLevel = 'INTERNSHIP' | 'TRAINEE' | 'JUNIOR' | 'MID' | 'SENIOR'

export interface Job {
  id: string
  companyId: string
  companyName: string
  title: string
  description: string
  area: ProfessionalArea
  location: string
  workModel: WorkModel
  level: JobLevel
  status: 'OPEN' | 'CLOSED'
  requiredSkills: Skill[]
  desiredSkills: Skill[]
  createdAt: string
  updatedAt: string
}

export interface RecommendedJob {
  job: Job
  match: MatchResult
}

export interface JobMatch {
  jobId: string
  jobTitle: string
  match: MatchResult
}

export interface JobFilters {
  search: string
  area: string
  workModel: string
  level: string
  location: string
}
