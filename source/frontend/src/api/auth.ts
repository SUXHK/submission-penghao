import { api } from './client'
import type { LoginRequest, LoginResponse, RegisterRequest, UserInfo } from '#/types/user'

export const authApi = {
  login: (data: LoginRequest) => api.post<LoginResponse>('/auth/login', data),
  register: (data: RegisterRequest) => api.post<LoginResponse>('/auth/register', data),
  me: () => api.get<UserInfo>('/auth/me'),
}
