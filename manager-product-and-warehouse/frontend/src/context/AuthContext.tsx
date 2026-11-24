import { createContext, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { createApiClient } from '../lib/api'
import type { ApiClient } from '../lib/api'
import { decodeJwt, normalizeBearerToken, signDevJwt } from '../lib/jwt'

export const devRoles = ['ADMIN', 'STAFF', 'CUSTOMER'] as const
export type DevRole = typeof devRoles[number]

export const rolePresets = [
  { role: 'ADMIN', user_id: 1 },
  { role: 'STAFF', user_id: 22 },
  { role: 'CUSTOMER', user_id: 105 },
] as const

const storageKey = 'dc-post.bearer-token'

interface AuthValue {
  token: string | null
  claims: ReturnType<typeof decodeJwt>
  devSigningEnabled: boolean
  api: ApiClient
  switchRole: (role: DevRole, userId: number) => Promise<void>
  useExternalToken: (value: string) => void
  clearSession: () => void
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(() => {
    try {
      return sessionStorage.getItem(storageKey)
    } catch {
      return null
    }
  })
  const api = useMemo(() => createApiClient(() => token), [token])

  function updateToken(value: string | null) {
    setToken(value)
    try {
      if (value === null) sessionStorage.removeItem(storageKey)
      else sessionStorage.setItem(storageKey, value)
    } catch {
      // The session still works in memory if browser storage is unavailable.
    }
  }

  const value: AuthValue = {
    token,
    claims: token ? decodeJwt(token) : null,
    devSigningEnabled: Boolean(import.meta.env.VITE_DEV_JWT_SECRET),
    api,
    async switchRole(role, userId) {
      const email = role.toLowerCase() + '.' + userId + '@dc-post.test'
      updateToken(await signDevJwt({ user_id: userId, email, role }))
    },
    useExternalToken(value) {
      const normalized = normalizeBearerToken(value)
      if (!normalized || /\s/.test(normalized)) throw new Error('Nhập một Bearer token hợp lệ.')
      updateToken(normalized)
    },
    clearSession: () => updateToken(null),
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth requires AuthProvider.')
  return auth
}
