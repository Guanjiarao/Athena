import { useEffect, useMemo, useState } from 'react'
import { isAxiosError } from 'axios'

import { getMyBlogList, type MyBlogRow } from '@/api/myBlogs'
import { useAuth } from '@/contexts/AuthContext'
import { cn } from '@/lib/utils'

function roleLabel(role: 'admin' | 'kol' | undefined): string {
  if (role === 'admin') return '管理员'
  if (role === 'kol') return '发布者'
  return '用户'
}

function statusMeta(status: number | null | undefined) {
  if (status === 0) {
    return {
      label: '待审核',
      badgeClass: 'border-amber-200 bg-amber-50 text-amber-700',
    }
  }
  if (status === 1) {
    return {
      label: '已通过',
      badgeClass: 'border-emerald-200 bg-emerald-50 text-emerald-700',
    }
  }
  if (status === 2) {
    return {
      label: '已驳回',
      badgeClass: 'border-red-200 bg-red-50 text-red-700',
    }
  }
  return {
    label: '未知状态',
    badgeClass: 'border-zinc-200 bg-zinc-50 text-zinc-600',
  }
}

export function SettingsPage() {
  const { user } = useAuth()
  const [rows, setRows] = useState<MyBlogRow[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (user?.role !== 'kol') return

    let cancelled = false
    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        const { data } = await getMyBlogList({ pageNum: 1, pageSize: 50 })
        if (cancelled) return
        setRows(Array.isArray(data?.data) ? data.data : [])
      } catch (e) {
        if (cancelled) return
        if (isAxiosError(e)) {
          const msg =
            (e.response?.data as { message?: string } | undefined)?.message ||
            e.message
          setError(msg || '加载个人信息失败，请稍后重试')
        } else {
          setError('加载个人信息失败，请稍后重试')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [user?.role])

  const pendingCount = useMemo(
    () => rows.filter((row) => row.status === 0).length,
    [rows],
  )
  const approvedCount = useMemo(
    () => rows.filter((row) => row.status === 1).length,
    [rows],
  )
  const rejectedRows = useMemo(
    () => rows.filter((row) => row.status === 2),
    [rows],
  )

  return (
    <div className="mx-auto max-w-6xl space-y-8">
      <header className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm md:p-8">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900">
          个人信息
        </h1>
        <p className="mt-2 text-sm text-zinc-500">
          这里可以查看当前账号信息，以及文章审核结果和驳回原因。
        </p>

        <div className="mt-6 grid gap-4 md:grid-cols-3">
          <div className="rounded-xl border border-zinc-200 bg-zinc-50/80 p-4">
            <p className="text-xs uppercase tracking-wide text-zinc-400">账号</p>
            <p className="mt-2 text-base font-medium text-zinc-900">
              {user?.username || '-'}
            </p>
          </div>
          <div className="rounded-xl border border-zinc-200 bg-zinc-50/80 p-4">
            <p className="text-xs uppercase tracking-wide text-zinc-400">身份</p>
            <p className="mt-2 text-base font-medium text-zinc-900">
              {roleLabel(user?.role)}
            </p>
          </div>
          <div className="rounded-xl border border-zinc-200 bg-zinc-50/80 p-4">
            <p className="text-xs uppercase tracking-wide text-zinc-400">
              驳回通知
            </p>
            <p className="mt-2 text-base font-medium text-zinc-900">
              {rejectedRows.length} 条
            </p>
          </div>
        </div>
      </header>

      {user?.role === 'kol' ? (
        <>
          <section className="grid gap-4 md:grid-cols-3">
            <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
              <p className="text-sm text-zinc-500">待审核文章</p>
              <p className="mt-3 text-3xl font-semibold tracking-tight text-zinc-900">
                {pendingCount}
              </p>
            </div>
            <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
              <p className="text-sm text-zinc-500">已通过文章</p>
              <p className="mt-3 text-3xl font-semibold tracking-tight text-zinc-900">
                {approvedCount}
              </p>
            </div>
            <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
              <p className="text-sm text-zinc-500">已驳回文章</p>
              <p className="mt-3 text-3xl font-semibold tracking-tight text-zinc-900">
                {rejectedRows.length}
              </p>
            </div>
          </section>

          <section className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm">
            <div className="mb-5">
              <h2 className="text-lg font-semibold text-zinc-900">驳回通知</h2>
              <p className="mt-1 text-sm text-zinc-500">
                点击右上角个人信息后，可以在这里查看被驳回文章的具体原因。
              </p>
            </div>

            {loading ? (
              <div className="rounded-xl border border-zinc-200 bg-zinc-50 p-8 text-center text-sm text-zinc-500">
                正在加载个人投稿数据...
              </div>
            ) : error ? (
              <div className="rounded-xl border border-zinc-200 bg-zinc-50 p-8 text-center text-sm text-red-500">
                {error}
              </div>
            ) : rejectedRows.length === 0 ? (
              <div className="rounded-xl border border-dashed border-zinc-200 bg-zinc-50/60 p-8 text-center text-sm text-zinc-500">
                当前没有被驳回的文章。
              </div>
            ) : (
              <div className="space-y-4">
                {rejectedRows.map((row) => (
                  <article
                    key={row.blogId}
                    className="overflow-hidden rounded-2xl border border-red-100 bg-red-50/40"
                  >
                    <div className="flex flex-col gap-4 p-4 md:flex-row md:p-5">
                      {row.coverUrl ? (
                        <img
                          src={row.coverUrl}
                          alt=""
                          className="h-28 w-full rounded-xl border border-red-100 object-cover md:w-44"
                        />
                      ) : (
                        <div className="flex h-28 w-full items-center justify-center rounded-xl border border-dashed border-red-100 bg-white/70 text-sm text-zinc-400 md:w-44">
                          无封面
                        </div>
                      )}
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <h3 className="text-base font-semibold text-zinc-900">
                            {row.title || '未命名文章'}
                          </h3>
                          <span
                            className={cn(
                              'inline-flex rounded-full border px-2.5 py-1 text-xs font-medium',
                              statusMeta(row.status).badgeClass,
                            )}
                          >
                            {statusMeta(row.status).label}
                          </span>
                        </div>
                        <p className="mt-2 text-sm text-zinc-500">
                          板块：{row.channelName || '-'} · 文章 ID：{row.blogId}
                        </p>
                        <div className="mt-4 rounded-xl border border-red-100 bg-white/80 p-4">
                          <p className="text-xs uppercase tracking-wide text-red-400">
                            驳回原因
                          </p>
                          <p className="mt-2 text-sm leading-6 text-zinc-700">
                            {row.reviewRemark || '暂无驳回备注'}
                          </p>
                        </div>
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>

          <section className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm">
            <div className="mb-5">
              <h2 className="text-lg font-semibold text-zinc-900">我的发布记录</h2>
              <p className="mt-1 text-sm text-zinc-500">
                这里会展示最近发布的文章及当前审核状态。
              </p>
            </div>

            {loading ? (
              <div className="rounded-xl border border-zinc-200 bg-zinc-50 p-8 text-center text-sm text-zinc-500">
                正在加载发布记录...
              </div>
            ) : error ? (
              <div className="rounded-xl border border-zinc-200 bg-zinc-50 p-8 text-center text-sm text-red-500">
                {error}
              </div>
            ) : rows.length === 0 ? (
              <div className="rounded-xl border border-dashed border-zinc-200 bg-zinc-50/60 p-8 text-center text-sm text-zinc-500">
                暂无发布记录。
              </div>
            ) : (
              <div className="space-y-3">
                {rows.map((row) => (
                  <article
                    key={row.blogId}
                    className="rounded-xl border border-zinc-200 bg-zinc-50/50 p-4"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <h3 className="truncate text-sm font-semibold text-zinc-900">
                          {row.title || '未命名文章'}
                        </h3>
                        <p className="mt-1 text-xs text-zinc-500">
                          板块：{row.channelName || '-'} · 点赞数：{row.likeTotal ?? 0}
                        </p>
                        {row.status === 2 && row.reviewRemark ? (
                          <p className="mt-3 text-sm leading-6 text-red-600">
                            驳回原因：{row.reviewRemark}
                          </p>
                        ) : null}
                      </div>
                      <span
                        className={cn(
                          'inline-flex rounded-full border px-2.5 py-1 text-xs font-medium',
                          statusMeta(row.status).badgeClass,
                        )}
                      >
                        {statusMeta(row.status).label}
                      </span>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </>
      ) : (
        <section className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-zinc-900">账号说明</h2>
          <p className="mt-2 text-sm leading-6 text-zinc-600">
            当前账号为管理员账号。发布者文章的驳回原因会在其个人信息页面中查看；管理员可以继续使用内容审核与发布文章功能。
          </p>
        </section>
      )}
    </div>
  )
}
