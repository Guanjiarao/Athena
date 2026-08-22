import axios from 'axios'
import type { AxiosRequestHeaders, InternalAxiosRequestConfig } from 'axios'

const AUTH_SESSION_STORAGE_KEY = 'athena-auth-session'

function readTokenFromStorage(): string | null {
  const fromLocalRaw = localStorage.getItem(AUTH_SESSION_STORAGE_KEY)
  if (fromLocalRaw) {
    try {
      const parsed = JSON.parse(fromLocalRaw) as { token?: unknown }
      if (typeof parsed.token === 'string' && parsed.token.trim()) {
        return parsed.token.trim()
      }
    } catch {
      // ignore invalid session payload
    }
  }

  const localToken = localStorage.getItem('token')
  if (localToken?.trim()) return localToken.trim()

  const sessionToken = sessionStorage.getItem('token')
  if (sessionToken?.trim()) return sessionToken.trim()

  return null
}

export const axiosInstance = axios.create()

axiosInstance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = readTokenFromStorage()
    if (!token) return config

    const headers = (config.headers ?? {}) as AxiosRequestHeaders
    headers.Authorization = `Bearer ${token}`
    config.headers = headers
    return config
  },
  (error) => Promise.reject(error),
)
