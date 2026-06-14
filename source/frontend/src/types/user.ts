export interface UserInfo {
  userId: number
  username: string
  displayName: string
  role: 'SUBMITTER' | 'ENGINEER' | 'QA'
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  displayName: string
  role: 'SUBMITTER' | 'ENGINEER' | 'QA'
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
  displayName: string
  role: 'SUBMITTER' | 'ENGINEER' | 'QA'
}
