import { http } from '@/shared/api/http'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  UserAccount,
} from '../types'

export const authApi = {
  login: (payload: LoginRequest) => http.post<LoginResponse>('/auth/login', payload).then((r) => r.data),
  register: (payload: RegisterRequest) =>
    http.post<RegisterResponse>('/auth/register', payload).then((r) => r.data),
  me: () => http.get<UserAccount>('/users/me').then((r) => r.data),
}
