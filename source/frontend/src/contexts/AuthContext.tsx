import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import type { LoginRequest, RegisterRequest, UserInfo } from '#/types/user'
import { authApi } from '#/api/auth'

interface AuthState {
  user: UserInfo | null
  isLoading: boolean
  login: (data: LoginRequest) => Promise<void>
  register: (data: RegisterRequest) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('token')
    if (token) {
      authApi.me()
        .then(u => setUser(u))
        .catch(() => { localStorage.removeItem('token'); localStorage.removeItem('user') })
        .finally(() => setIsLoading(false))
    } else {
      setIsLoading(false)
    }
  }, [])

  const login = useCallback(async (data: LoginRequest) => {
    const res = await authApi.login(data)
    localStorage.setItem('token', res.token)
    setUser({ userId: res.userId, username: res.username, displayName: res.displayName, role: res.role })
  }, [])

  const register = useCallback(async (data: RegisterRequest) => {
    await authApi.register(data)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, isLoading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be inside AuthProvider')
  return ctx
}
