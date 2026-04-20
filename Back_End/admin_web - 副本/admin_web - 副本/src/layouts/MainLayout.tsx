import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import {
  ChevronDown,
  FileSearch,
  LogOut,
  Newspaper,
  Rocket,
  User,
} from 'lucide-react'

import { useAuth } from '@/contexts/AuthContext'
import { cn } from '@/lib/utils'

const sidebarNavLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'relative flex items-center gap-3 rounded-lg py-2.5 pr-3 pl-3 text-lg transition-colors',
    isActive
      ? 'font-medium text-zinc-900 before:pointer-events-none before:absolute before:top-1/2 before:left-0 before:h-12 before:w-1 before:-translate-y-1/2 before:rounded-r-sm before:bg-zinc-900'
      : 'font-normal text-zinc-500 hover:bg-zinc-100 hover:text-zinc-800',
  )

function UserMenu() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  const displayName =
    user?.role === 'admin' ? '管理员' : user?.role === 'kol' ? '创作者' : '用户'
  const initial = user?.username?.slice(0, 1).toUpperCase() ?? '?'

  useEffect(() => {
    if (!open) return
    const onPointerDown = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 rounded-full border border-zinc-200/90 bg-white py-1 pr-2 pl-1 shadow-sm transition hover:border-zinc-300"
      >
        <span
          className="flex size-9 items-center justify-center rounded-full bg-zinc-900 text-xs font-semibold text-white"
          aria-hidden
        >
          {initial}
        </span>
        <span className="hidden text-left text-sm leading-tight sm:block">
          <span className="block font-medium text-zinc-900">{displayName}</span>
          <span className="text-xs text-zinc-500">
            {user?.username ?? ''} · {user?.role ?? ''}
          </span>
        </span>
        <ChevronDown
          className={cn(
            'size-4 text-zinc-400 transition-transform',
            open && 'rotate-180',
          )}
          aria-hidden
        />
      </button>

      <AnimatePresence>
        {open ? (
          <motion.div
            initial={{ opacity: 0, y: 6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 6, scale: 0.98 }}
            transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
            role="menu"
            className="absolute top-[calc(100%+10px)] right-0 z-50 min-w-[200px] overflow-hidden rounded-xl border border-zinc-200/90 bg-white py-1 shadow-lg shadow-zinc-900/10"
          >
            <button
              type="button"
              role="menuitem"
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-zinc-700 transition hover:bg-zinc-50"
              onClick={() => setOpen(false)}
            >
              <User className="size-4 text-zinc-400" aria-hidden />
              个人设置
            </button>
            <button
              type="button"
              role="menuitem"
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-zinc-700 transition hover:bg-zinc-50"
              onClick={() => {
                setOpen(false)
                logout()
                navigate('/login', { replace: true })
              }}
            >
              <LogOut className="size-4 text-zinc-400" aria-hidden />
              退出登录
            </button>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  )
}

export function MainLayout() {
  const location = useLocation()
  const { user } = useAuth()

  return (
    <div className="flex min-h-svh bg-zinc-50">
      <aside
        className="flex w-[260px] shrink-0 flex-col bg-white shadow-[6px_0_32px_-12px_rgba(15,23,42,0.06)]"
        aria-label="主导航"
      >
        <div className="px-6 pt-7 pb-6">
          <div className="text-[11px] font-semibold tracking-[0.2em] text-zinc-800 uppercase">
            Athena
          </div>
          <div className="mt-1 text-2xl font-semibold tracking-tight text-zinc-900">
            审核中台
          </div>
        </div>
        <nav className="flex flex-col gap-0.5 px-3 pb-6">
          {user?.role === 'admin' ? (
            <>
              <NavLink to="/audit" className={sidebarNavLinkClass} end>
                <FileSearch className="size-5 shrink-0 text-zinc-800" aria-hidden />
                内容审核
              </NavLink>
              <NavLink to="/admin/publish" className={sidebarNavLinkClass}>
                <Newspaper className="size-5 shrink-0 text-zinc-800" aria-hidden />
                发布文章
              </NavLink>
            </>
          ) : null}
          {user?.role === 'kol' ? (
            <NavLink to="/publish" className={sidebarNavLinkClass}>
              <Rocket className="size-5 shrink-0 text-zinc-800" aria-hidden />
              内容发布
            </NavLink>
          ) : null}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 shrink-0 items-center justify-end border-b border-zinc-200/80 bg-white/80 px-6 backdrop-blur-md">
          <UserMenu />
        </header>

        <div className="min-h-0 flex-1 overflow-auto bg-zinc-50">
          <div className="mx-auto max-w-[1400px] p-6 md:p-8">
            <AnimatePresence mode="wait">
              <motion.div
                key={location.pathname}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: 6 }}
                transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
              >
                <Outlet />
              </motion.div>
            </AnimatePresence>
          </div>
        </div>
      </div>
    </div>
  )
}
