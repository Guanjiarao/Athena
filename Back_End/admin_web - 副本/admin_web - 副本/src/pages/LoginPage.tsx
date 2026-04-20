import { type FormEvent, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'

import { useAuth, type UserRole } from '@/contexts/AuthContext'
import { cn } from '@/lib/utils'

const SYSTEM_VERSION = '0.0.0'

type LoginPortal = 'admin' | 'kol'

type LoginApiBody = {
  phone: string
  code: string
}

type LoginApiResponse = {
  code: number
  message: string
  data: unknown
}

function digitsOnly(value: string): string {
  return value.replace(/\D/g, '')
}

function extractToken(data: unknown): string {
  if (typeof data === 'string') return data
  if (data && typeof data === 'object' && 'token' in data && typeof (data as { token: unknown }).token === 'string') {
    return (data as { token: string }).token
  }
  return String(data ?? '')
}

/** 登录 data 中解析用户 id，写入会话供发布等接口使用 */
function extractUserIdFromLoginData(data: unknown): number | undefined {
  if (!data || typeof data !== 'object') return undefined
  const o = data as Record<string, unknown>

  const tryNum = (v: unknown): number | undefined => {
    if (typeof v === 'number' && Number.isFinite(v) && v > 0) return Math.trunc(v)
    if (typeof v === 'string' && v.trim()) {
      const n = Number(v.trim())
      if (!Number.isNaN(n) && n > 0) return Math.trunc(n)
    }
    return undefined
  }

  const direct =
    tryNum(o.userId) ?? tryNum(o.id) ?? tryNum(o.user_id)
  if (direct !== undefined) return direct

  const user = o.user
  if (user && typeof user === 'object') {
    const u = user as Record<string, unknown>
    return tryNum(u.id) ?? tryNum(u.userId)
  }

  return undefined
}

export function LoginPage() {
  const { user, establishSession } = useAuth()
  const navigate = useNavigate()
  const [portal, setPortal] = useState<LoginPortal>('admin')
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const selectPortal = (p: LoginPortal) => {
    setPortal(p)
    setError(null)
  }

  if (user) {
    return (
      <Navigate
        to={user.role === 'admin' ? '/audit' : '/publish'}
        replace
      />
    )
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    const phoneDigits = digitsOnly(phone)
    if (!phoneDigits) {
      setError('请输入手机号')
      return
    }
    if (!code.trim()) {
      setError('请输入验证码')
      return
    }

    setLoading(true)
    try {
      const body: LoginApiBody = {
        phone: phoneDigits,
        code: code.trim(),
      }

      const res = await fetch('/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })

      let json: LoginApiResponse | null = null
      try {
        json = (await res.json()) as LoginApiResponse
      } catch {
        setError('服务响应异常，请稍后重试')
        return
      }

      if (!json || json.code !== 200) {
        const msg =
          json && typeof json.message === 'string' && json.message
            ? json.message
            : '登录失败，请检查手机号与验证码'
        setError(msg)
        return
      }

      const token = extractToken(json.data)
      if (!token) {
        setError('登录响应缺少凭证，请联系管理员')
        return
      }

      // 接口暂未返回角色：以登录页所选入口为准（与「系统管理员 / 认证创作者」一致）
      const role: UserRole = portal === 'admin' ? 'admin' : 'kol'
      const userId = extractUserIdFromLoginData(json.data)
      establishSession({
        token,
        phone: phoneDigits,
        role,
        ...(userId !== undefined ? { userId } : {}),
      })
      navigate(role === 'admin' ? '/audit' : '/publish', { replace: true })
    } catch {
      setError('网络异常，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-zinc-950 md:flex-row">
      <aside className="relative flex min-h-[280px] w-full flex-col border-b border-zinc-800/80 md:min-h-screen md:w-1/2 md:border-b-0 md:border-r md:border-zinc-800/80">
        <div className="px-8 pt-10 pb-6 md:px-12 md:pt-14 md:pb-8">
          <div className="text-lg font-semibold tracking-tight text-white md:text-xl">
            Athena
          </div>
        </div>

        <div className="flex flex-1 flex-col items-center justify-center px-8 py-10 md:px-14 md:py-16">
          <motion.p
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
            className="max-w-md text-center text-[1.125rem] font-light leading-[1.75] tracking-wide text-zinc-200 md:text-xl md:leading-[1.8]"
          >
            用理性的目光，丈量独属于你的生命旷野。
          </motion.p>
        </div>

        <footer className="mt-auto border-t border-zinc-800/60 px-8 py-8 md:px-12 md:py-10">
          <p className="text-[11px] font-medium tabular-nums tracking-wide text-zinc-500">
            版本 v{SYSTEM_VERSION}
          </p>
          <p className="mt-2 text-[11px] leading-relaxed text-zinc-600">
            © {new Date().getFullYear()} Athena. 保留所有权利。
          </p>
        </footer>
      </aside>

      <main className="flex w-full flex-1 items-center justify-center bg-zinc-50 px-6 py-14 md:w-1/2 md:min-h-screen md:py-12">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
          className="w-full max-w-[400px]"
        >
          <div
            className="mb-8 flex rounded-full border border-zinc-200/90 bg-zinc-100/90 p-1 shadow-sm"
            role="tablist"
            aria-label="选择登录身份"
          >
            <button
              type="button"
              role="tab"
              aria-selected={portal === 'admin'}
              onClick={() => selectPortal('admin')}
              className={cn(
                'flex flex-1 items-center justify-center gap-1.5 rounded-full py-2.5 text-sm font-medium transition-colors',
                portal === 'admin'
                  ? 'bg-zinc-900 text-white shadow-sm'
                  : 'bg-transparent text-zinc-500 hover:text-zinc-700',
              )}
            >
              系统管理员
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={portal === 'kol'}
              onClick={() => selectPortal('kol')}
              className={cn(
                'flex flex-1 items-center justify-center gap-1.5 rounded-full py-2.5 text-sm font-medium transition-colors',
                portal === 'kol'
                  ? 'bg-zinc-900 text-white shadow-sm'
                  : 'bg-transparent text-zinc-500 hover:text-zinc-700',
              )}
            >
              认证创作者
            </button>
          </div>

          <h1 className="mb-8 text-2xl font-semibold tracking-tight text-zinc-900 md:text-[1.65rem]">
            {portal === 'admin' ? '登录审核中台' : '登录创作者门户'}
          </h1>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div>
              <label
                htmlFor="login-phone"
                className="mb-1.5 block text-xs font-medium tracking-wide text-zinc-500"
              >
                手机号（Phone）
              </label>
              <input
                id="login-phone"
                name="phone"
                type="text"
                inputMode="numeric"
                autoComplete="tel"
                maxLength={11}
                value={phone}
                onChange={(e) => setPhone(digitsOnly(e.target.value))}
                placeholder="请输入手机号"
                className="w-full rounded-xl border border-zinc-200 bg-white px-4 py-3.5 text-sm text-zinc-900 tabular-nums placeholder:text-zinc-400 shadow-sm outline-none transition focus:border-zinc-900 focus:ring-1 focus:ring-zinc-900"
              />
            </div>
            <div>
              <label
                htmlFor="login-code"
                className="mb-1.5 block text-xs font-medium tracking-wide text-zinc-500"
              >
                验证码（Code）
              </label>
              <input
                id="login-code"
                name="code"
                type="text"
                autoComplete="one-time-code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="请输入验证码"
                className="w-full rounded-xl border border-zinc-200 bg-white px-4 py-3.5 text-sm text-zinc-900 placeholder:text-zinc-400 shadow-sm outline-none transition focus:border-zinc-900 focus:ring-1 focus:ring-zinc-900"
              />
            </div>

            {error ? (
              <p className="text-center text-xs leading-relaxed text-red-500" role="alert">
                {error}
              </p>
            ) : null}

            <motion.button
              type="submit"
              disabled={loading}
              whileHover={loading ? undefined : { scale: 1.005 }}
              whileTap={loading ? undefined : { scale: 0.99 }}
              transition={{ type: 'spring', stiffness: 480, damping: 28 }}
              className={cn(
                'mt-1 w-full rounded-xl bg-zinc-900 py-3.5 text-sm font-semibold text-white shadow-sm transition',
                'hover:bg-zinc-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900',
                loading && 'pointer-events-none opacity-70',
              )}
            >
              {loading ? '登录中...' : '登录'}
            </motion.button>
          </form>

          <p className="mt-8 text-center text-[11px] leading-relaxed text-zinc-400">
            内部系统，仅限受邀创作者与管理员访问。如需入驻请联系运营。
          </p>
        </motion.div>
      </main>
    </div>
  )
}
