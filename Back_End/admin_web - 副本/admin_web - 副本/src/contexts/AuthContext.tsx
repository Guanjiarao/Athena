import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

export type UserRole = 'admin' | 'kol'

export type AuthUser = {
  /** 展示用，一般为手机号 */
  username: string
  role: UserRole
  token: string
  /** 后端用户 id；登录接口若返回可写入，缺省时业务层可用占位 */
  userId?: number
}

const STORAGE_KEY = 'athena-auth-session'

type StoredSession = {
  token: string
  username: string
  role: UserRole
  userId?: number
}

type AuthContextValue = {
  user: AuthUser | null
  /** 登录成功后写入会话（token + 用户信息） */
  establishSession: (payload: {
    token: string
    phone: string
    role: UserRole
    userId?: number
  }) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readStoredSession(): AuthUser | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as unknown
    if (
      parsed &&
      typeof parsed === 'object' &&
      'token' in parsed &&
      'username' in parsed &&
      'role' in parsed &&
      typeof (parsed as StoredSession).token === 'string' &&
      typeof (parsed as StoredSession).username === 'string' &&
      ((parsed as StoredSession).role === 'admin' ||
        (parsed as StoredSession).role === 'kol')
    ) {
      const s = parsed as StoredSession
      const uid = s.userId
      return {
        username: s.username,
        role: s.role,
        token: s.token,
        ...(typeof uid === 'number' && !Number.isNaN(uid) ? { userId: uid } : {}),
      }
    }
    return null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => readStoredSession())

  useEffect(() => {
    if (user) {
      const payload: StoredSession = {
        token: user.token,
        username: user.username,
        role: user.role,
        ...(typeof user.userId === 'number' && !Number.isNaN(user.userId)
          ? { userId: user.userId }
          : {}),
      }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [user])

  const establishSession = useCallback(
    (payload: {
      token: string
      phone: string
      role: UserRole
      userId?: number
    }) => {
      setUser({
        token: payload.token,
        username: payload.phone,
        role: payload.role,
        ...(typeof payload.userId === 'number' && !Number.isNaN(payload.userId)
          ? { userId: payload.userId }
          : {}),
      })
    },
    [],
  )

  const logout = useCallback(() => {
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({
      user,
      establishSession,
      logout,
    }),
    [user, establishSession, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
