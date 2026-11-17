export interface CompanyProfile {
  id: string
  name: string
  sector: string | null
  description: string | null
  location: string | null
}
export interface CompanyProfileRequest {
  name: string
  sector?: string
  description?: string
  location?: string
}
