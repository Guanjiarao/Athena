import axios from 'axios'

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
      // ignore
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
  (config) => {
    console.log('【Debug axios】拦截器触发，URL:', config.url)
    const token = readTokenFromStorage()
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
      console.log('【Debug axios】已注入 Authorization header')
    } else {
      console.warn('【Debug axios】未读取到 token，跳过 Authorization')
    }
    return config
  },
  (error) => {
    console.error('【Debug axios】请求拦截器异常：', error)
    return Promise.reject(error)
  },
)

axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('【Debug axios】响应拦截器捕获异常：', error)
    return Promise.reject(error)
  },
)
