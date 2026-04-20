import { useCallback, useEffect, useMemo, useState } from 'react'
import { isAxiosError } from 'axios'

import {
  approveNote,
  getPendingList,
  getReviewDetail,
  rejectNote,
  type AuditPendingRow,
  type AuditReviewDetail,
} from '@/api/audit'
import { cn } from '@/lib/utils'

function typeLabel(type: number): string {
  if (type === 0) return '科普视频'
  if (type === 127) return '核心专区'
  if (type > 0) return '公众号图文'
  return `未知类型(${type})`
}

export function AuditList() {
  const [rows, setRows] = useState<AuditPendingRow[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [activeNoteId, setActiveNoteId] = useState<number | null>(null)
  const [detail, setDetail] = useState<AuditReviewDetail | null>(null)

  const [deciding, setDeciding] = useState(false)

  const loadPending = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await getPendingList({ pageNum: 1, pageSize: 10 })
      const list = data?.data
      if (!Array.isArray(list)) {
        console.log('[AuditList] unexpected pending payload:', data)
      }
      setRows(Array.isArray(list) ? list : [])
    } catch (e) {
      if (isAxiosError(e)) {
        const msg =
          (e.response?.data as { message?: string } | undefined)?.message ||
          e.message
        setError(msg || '加载失败，请稍后重试')
      } else {
        setError('加载失败，请稍后重试')
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadPending()
  }, [loadPending])

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(null), 2800)
    return () => window.clearTimeout(timer)
  }, [toast])

  const parseImgUrls = useCallback((value: unknown): string[] => {
    if (Array.isArray(value)) {
      return value.filter((v): v is string => typeof v === 'string' && !!v)
    }
    if (typeof value === 'string' && value.trim()) {
      try {
        const parsed = JSON.parse(value) as unknown
        if (Array.isArray(parsed)) {
          return parsed.filter((v): v is string => typeof v === 'string' && !!v)
        }
      } catch {
        return []
      }
    }
    return []
  }, [])

  const openReview = useCallback(async (blogId: number) => {
    setActiveNoteId(blogId)
    setDrawerOpen(true)
    setDetail(null)
    setDetailError(null)
    setDetailLoading(true)
    try {
      const { data } = await getReviewDetail(blogId)
      setDetail(data?.data ?? null)
    } catch (e) {
      if (isAxiosError(e)) {
        const msg =
          (e.response?.data as { message?: string } | undefined)?.message ||
          e.message
        setDetailError(msg || '详情加载失败，请重试')
      } else {
        setDetailError('详情加载失败，请重试')
      }
    } finally {
      setDetailLoading(false)
    }
  }, [])

  const closeDrawer = useCallback(() => {
    if (deciding) return
    setDrawerOpen(false)
    setDetailError(null)
    setDetailLoading(false)
  }, [deciding])

  const handleDecision = useCallback(
    async (action: 'approve' | 'reject') => {
      if (!activeNoteId) return
      setDeciding(true)
      setDetailError(null)
      try {
        const resp =
          action === 'approve'
            ? await approveNote(activeNoteId)
            : await rejectNote(activeNoteId)
        if (resp.data?.code === 200) {
          setToast(action === 'approve' ? '已通过审核' : '已驳回')
          closeDrawer()
          await loadPending()
          return
        }
        setDetailError(resp.data?.message || '操作失败，请稍后重试')
      } catch (e) {
        if (isAxiosError(e)) {
          const msg =
            (e.response?.data as { message?: string } | undefined)?.message ||
            e.message
          setDetailError(msg || '操作失败，请稍后重试')
        } else {
          setDetailError('操作失败，请稍后重试')
        }
      } finally {
        setDeciding(false)
      }
    },
    [activeNoteId, closeDrawer, loadPending],
  )

  const content = useMemo(() => {
    if (loading) {
      return (
        <div className="rounded-xl border border-zinc-200 bg-white p-10 text-center text-sm text-zinc-500">
          正在加载待审核列表...
        </div>
      )
    }
    if (error) {
      return (
        <div className="rounded-xl border border-zinc-200 bg-white p-10 text-center text-sm text-red-500">
          {error}
        </div>
      )
    }
    if (rows.length === 0) {
      return (
        <div className="rounded-xl border border-zinc-200 bg-white p-10 text-center text-sm text-zinc-500">
          当前暂无待审核内容
        </div>
      )
    }
    return (
      <div className="overflow-x-auto rounded-xl border border-zinc-200 bg-white">
        <table className="w-full min-w-[760px] border-collapse">
          <thead>
            <tr className="border-b border-zinc-200 bg-zinc-50">
              <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">ID</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">标题</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">内容类型</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">所属板块</th>
              <th className="sticky right-0 z-10 bg-zinc-50 px-4 py-3 text-right text-xs font-medium text-zinc-500">
                操作
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100">
            {rows.map((row) => (
              <tr key={row.blogId} className="transition-colors hover:bg-zinc-50/80">
                <td className="px-4 py-3 text-sm tabular-nums text-zinc-700">{row.blogId}</td>
                <td className="max-w-[360px] px-4 py-3 text-sm font-medium text-zinc-900">
                  <span className="line-clamp-2">{row.title || '-'}</span>
                </td>
                <td className="px-4 py-3 text-sm text-zinc-700">{typeLabel(row.type)}</td>
                <td className="px-4 py-3 text-sm text-zinc-700">{row.channelName || '-'}</td>
                <td className="sticky right-0 z-10 bg-white px-4 py-3 text-right">
                  <button
                    type="button"
                    onClick={() => void openReview(row.blogId)}
                    className={cn(
                      'rounded-md border border-zinc-900 bg-zinc-900 px-3 py-1.5 text-xs font-medium text-white',
                      'transition hover:bg-zinc-800',
                    )}
                  >
                    进行审核
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }, [error, loading, rows])

  const detailImgUrls = parseImgUrls(detail?.imgUrls)

  return (
    <>
      <section>
        <header className="mb-6">
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900">内容审核</h1>
          <p className="mt-1.5 text-sm text-zinc-500">
            当前展示待审核队列（默认第 1 页，每页 10 条）。
          </p>
        </header>
        {content}
      </section>

      {drawerOpen ? (
        <div className="fixed inset-0 z-50 flex justify-end">
          <button
            type="button"
            className="absolute inset-0 bg-zinc-950/35"
            onClick={closeDrawer}
            aria-label="关闭审核详情"
          />
          <aside className="relative z-10 flex h-full w-full max-w-3xl flex-col border-l border-zinc-200 bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-zinc-200 px-6 py-4">
              <div>
                <h2 className="text-lg font-semibold text-zinc-900">审核详情</h2>
                <p className="mt-0.5 text-xs text-zinc-500 tabular-nums">
                  Note ID: {activeNoteId ?? '-'}
                </p>
              </div>
              <button
                type="button"
                onClick={closeDrawer}
                className="rounded-md px-2 py-1 text-sm text-zinc-500 transition hover:bg-zinc-100 hover:text-zinc-700"
              >
                关闭
              </button>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
              {detailLoading ? (
                <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-8 text-center text-sm text-zinc-500">
                  正在加载详情...
                </div>
              ) : null}

              {!detailLoading && detailError ? (
                <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-8 text-center text-sm text-red-500">
                  {detailError}
                </div>
              ) : null}

              {!detailLoading && !detailError && detail ? (
                <div className="space-y-6">
                  <div>
                    <h3 className="text-base font-semibold text-zinc-900">
                      {detail.title || '未命名内容'}
                    </h3>
                    <p className="mt-1 text-xs text-zinc-500">
                      类型：{typeof detail.type === 'number' ? typeLabel(detail.type) : '-'} ·
                      板块：{detail.channelName || '-'}
                    </p>
                  </div>

                  {detail.videoUrl ? (
                    <div>
                      <p className="mb-2 text-xs font-medium uppercase tracking-wide text-zinc-400">
                        视频内容
                      </p>
                      <video
                        controls
                        src={detail.videoUrl}
                        className="w-full rounded-lg border border-zinc-200 bg-black"
                      />
                    </div>
                  ) : null}

                  {detailImgUrls.length > 0 ? (
                    <div>
                      <p className="mb-2 text-xs font-medium uppercase tracking-wide text-zinc-400">
                        图片内容
                      </p>
                      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                        {detailImgUrls.map((url) => (
                          <img
                            key={url}
                            src={url}
                            alt=""
                            className="aspect-square w-full rounded-md border border-zinc-200 object-cover"
                          />
                        ))}
                      </div>
                    </div>
                  ) : null}

                  {detail.content ? (
                    <div>
                      <p className="mb-2 text-xs font-medium uppercase tracking-wide text-zinc-400">
                        正文内容
                      </p>
                      <article
                        className="prose prose-zinc max-w-none rounded-lg border border-zinc-200 p-4 text-sm"
                        dangerouslySetInnerHTML={{ __html: detail.content }}
                      />
                    </div>
                  ) : null}
                </div>
              ) : null}
            </div>

            <div className="flex items-center justify-end gap-3 border-t border-zinc-200 px-6 py-4">
              <button
                type="button"
                disabled={deciding || detailLoading}
                onClick={() => void handleDecision('reject')}
                className={cn(
                  'rounded-md border border-zinc-300 bg-white px-4 py-2 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50',
                  (deciding || detailLoading) && 'pointer-events-none opacity-60',
                )}
              >
                {deciding ? '处理中...' : '驳回'}
              </button>
              <button
                type="button"
                disabled={deciding || detailLoading}
                onClick={() => void handleDecision('approve')}
                className={cn(
                  'rounded-md border border-zinc-900 bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-zinc-800',
                  (deciding || detailLoading) && 'pointer-events-none opacity-60',
                )}
              >
                {deciding ? '处理中...' : '通过审核'}
              </button>
            </div>
          </aside>
        </div>
      ) : null}

      {toast ? (
        <div className="fixed bottom-8 left-1/2 z-50 -translate-x-1/2 rounded-lg border border-zinc-800 bg-zinc-900 px-4 py-2.5 text-sm text-white shadow-lg">
          {toast}
        </div>
      ) : null}
    </>
  )
}
