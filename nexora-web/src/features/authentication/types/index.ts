export type UserRole = 'STUDENT' | 'COMPANY' | 'UNIVERSITY' | 'ADMIN'
export type AccountStatus = 'ACTIVE' | 'PENDING_APPROVAL' | 'SUSPENDED' | 'REJECTED'
export type PublicRole = Exclude<UserRole, 'ADMIN'>

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  role: UserRole
}

export interface UniversityProfileRequest {
  name: string
  acronym?: string
  location?: string
  website?: string
  description?: string
}

export interface RegisterRequest {
  email: string
  password: string
  role: PublicRole
  university?: UniversityProfileRequest
}

export interface RegisterResponse {
  id: string
  email: string
  role: UserRole
  status: AccountStatus
}

export interface UserAccount {
  id: string
  email: string
  role: UserRole
  status: AccountStatus
  createdAt: string
}
