export type ProfessionalArea =
  | 'BACKEND' | 'FRONTEND' | 'FULLSTACK' | 'MOBILE' | 'DATA' | 'ARTIFICIAL_INTELLIGENCE' | 'DEVOPS_CLOUD'
  | 'INFRASTRUCTURE' | 'SECURITY' | 'QA' | 'UX_UI' | 'EMBEDDED_IOT' | 'GAMES' | 'PRODUCT_MANAGEMENT'
  | 'SUPPORT' | 'OTHER'
export type WorkModel = 'ON_SITE' | 'REMOTE' | 'HYBRID'

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Skill {
  id: string
  name: string
}
export interface AreaOption {
  code: ProfessionalArea
  label: string
}

/** GET/PUT /students/me */
export interface StudentProfile {
  id: string
  fullName: string
  course: string | null
  institution: string | null
  location: string | null
  summary: string | null
}
export interface StudentProfileRequest {
  fullName: string
  course?: string
  institution?: string
  location?: string
  summary?: string
}

export interface Education {
  institution: string
  course: string
  degree?: string | null
  startYear: number
  endYear?: number | null
}
export interface Experience {
  company: string
  role: string
  description?: string | null
  startDate: string
  endDate?: string | null
}
export interface Project {
  name: string
  description?: string | null
  url?: string | null
}
export interface Course {
  name: string
  institution?: string | null
  year?: number | null
  certificateUrl?: string | null
}
export interface Links {
  github?: string | null
  linkedin?: string | null
  portfolio?: string | null
}

/** GET/PUT /students/me/resume (substituição integral). */
export interface Resume {
  education: Education[]
  experiences: Experience[]
  projects: Project[]
  courses: Course[]
  softSkills: string[]
  interests: string[]
  links: Links
  interestAreas: ProfessionalArea[]
  preferredWorkModels: WorkModel[]
}
