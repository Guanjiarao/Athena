import { useCallback, useEffect, useMemo, useState } from 'react'
import { isAxiosError } from 'axios'
import {
  Eye,
  FileText,
  ImageIcon,
  RefreshCw,
  Trash2,
  Video,
  X,
} from 'lucide-react'

import {
  deletePublishedContent,
  getContentListByChannel,
  getContentListByType,
  getPublishedContentDetail,
  getSquareContentList,
  type ContentListRow,
  type PublishedContentDetail,
} from '@/api/contentManage'
import {
  ADMIN_CONTENT_MODULES,
  SCIENCE_CONTENT_GROUPS,
  getContentOptionById,
  getDefaultOptionId,
  resolveContentCategoryLabel,
  resolveContentKindLabel,
  resolveContentModuleLabel,
  resolveContentParentLabel,
  resolveContentTypeLabel,
  resolveStatusMeta,
  type AdminContentModuleKey,
  type AdminContentOption,
} from '@/lib/content-management'
import { cn } from '@/lib/utils'

const PAGE_SIZE_OPTIONS = [10, 20, 50]
const RAW_FETCH_BATCH_SIZE = 50
const MAX_RAW_FETCH_PAGES = 80

function getErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError(error)) {
    const message =
      (error.response?.data as { message?: string } | undefined)?.message ||
      error.message
    return message || fallback
  }

  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}

function ensureApiSuccess(
  data: { code?: number; message?: string } | undefined,
  fallback: string,
) {
  if (typeof data?.code === 'number' && data.code !== 200) {
    throw new Error(data.message || fallback)
  }
}

function parseImgUrls(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is string => typeof item === 'string' && !!item)
  }

  if (typeof value === 'string' && value.trim()) {
    try {
      const parsed = JSON.parse(value) as unknown
      if (Array.isArray(parsed)) {
        return parsed.filter(
          (item): item is string => typeof item === 'string' && !!item,
        )
      }
    } catch {
      return []
    }
  }

  return []
}

function looksLikeHtml(content: string) {
  return /<\/?[a-z][\s\S]*>/i.test(content)
}

function normalizeChannelId(value?: number | null) {
  return typeof value === 'number' ? value : 0
}

function matchesActiveOption(row: ContentListRow, option: AdminContentOption) {
  const rowType = row.type
  const rowChannelId = normalizeChannelId(row.channelId)

  if (option.id === 'square-all') {
    return rowType === 1 || rowType === 2
  }

  if (option.moduleKey === 'square') {
    return typeof option.type === 'number' && rowType === option.type
  }

  if (option.moduleKey === 'recommend') {
    return rowType === 0
  }

  if (option.moduleKey === 'science') {
    if (typeof option.type !== 'number') return false

    if (typeof option.channelId === 'number' && option.channelId > 0) {
      return rowType === option.type && rowChannelId === option.channelId
    }

    return (
      rowType === option.type &&
      rowChannelId === normalizeChannelId(option.channelId)
    )
  }

  return true
}

function ModuleButton({
  label,
  description,
  active,
  onClick,
}: {
  label: string
  description: string
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex min-w-[180px] flex-1 flex-col rounded-2xl border px-4 py-4 text-left transition',
        active
          ? 'border-zinc-900 bg-zinc-900 text-white shadow-sm'
          : 'border-zinc-200 bg-white text-zinc-900 hover:border-zinc-300 hover:bg-zinc-50',
      )}
    >
      <span className="text-sm font-semibold">{label}</span>
      <span
        className={cn(
          'mt-1.5 text-xs leading-5',
          active ? 'text-zinc-300' : 'text-zinc-500',
        )}
      >
        {description}
      </span>
    </button>
  )
}

function OptionButton({
  option,
  active,
  onClick,
}: {
  option: AdminContentOption
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'w-full rounded-2xl border px-4 py-4 text-left transition',
        active
          ? 'border-zinc-900 bg-zinc-900 text-white shadow-sm'
          : 'border-zinc-200 bg-white text-zinc-900 hover:border-zinc-300 hover:bg-zinc-50',
      )}
    >
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm font-semibold">{option.shortLabel}</span>
        <span
          className={cn(
            'rounded-full px-2 py-1 text-[11px] font-medium',
            active ? 'bg-white/12 text-zinc-100' : 'bg-zinc-100 text-zinc-600',
          )}
        >
          {option.contentKind === 'note'
            ? '笔记'
            : option.contentKind === 'video'
              ? '视频'
              : option.contentKind === 'article'
                ? '文章'
                : '全部'}
        </span>
      </div>
      <p
        className={cn(
          'mt-2 text-xs leading-5',
          active ? 'text-zinc-300' : 'text-zinc-500',
        )}
      >
        {option.description}
      </p>
    </button>
  )
}

function StatusBadge({ status }: { status?: number | null }) {
  const meta = resolveStatusMeta(status)
  return (
    <span
      className={cn(
        'inline-flex rounded-full border px-2.5 py-1 text-xs font-medium',
        meta.className,
      )}
    >
      {meta.label}
    </span>
  )
}

function PreviewThumb({
  coverUrl,
  type,
}: {
  coverUrl?: string | null
  type?: number | null
}) {
  const isVideo = type === 0 || type === 2
  const Icon = isVideo ? Video : type === 1 ? ImageIcon : FileText

  if (coverUrl) {
    return (
      <div className="relative h-16 w-16 overflow-hidden rounded-xl border border-zinc-200 bg-zinc-100">
        <img
          src={coverUrl}
          alt=""
          className="h-full w-full object-cover"
          loading="lazy"
        />
        <span className="absolute right-1 bottom-1 rounded bg-black/60 px-1.5 py-0.5 text-[10px] text-white">
          {resolveContentKindLabel(type)}
        </span>
      </div>
    )
  }

  return (
    <div className="flex h-16 w-16 items-center justify-center rounded-xl border border-dashed border-zinc-200 bg-zinc-50 text-zinc-400">
      <Icon className="size-5" aria-hidden />
    </div>
  )
}

function DetailField({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <div className="rounded-xl border border-zinc-200 bg-zinc-50/70 px-4 py-3">
      <p className="text-xs uppercase tracking-wide text-zinc-400">{label}</p>
      <p className="mt-1.5 text-sm font-medium text-zinc-900">{value}</p>
    </div>
  )
}

function ContentBody({ content }: { content?: string | null }) {
  const trimmed = content?.trim()
  if (!trimmed) return null

  if (looksLikeHtml(trimmed)) {
    return (
      <article
        className="prose prose-zinc max-w-none rounded-2xl border border-zinc-200 bg-white p-5 text-sm"
        dangerouslySetInnerHTML={{ __html: trimmed }}
      />
    )
  }

  return (
    <div className="whitespace-pre-wrap rounded-2xl border border-zinc-200 bg-white p-5 text-sm leading-7 text-zinc-700">
      {trimmed}
    </div>
  )
}

export function ContentManagePage() {
  const [moduleKey, setModuleKey] = useState<AdminContentModuleKey>('square')
  const [activeOptionId, setActiveOptionId] = useState(() =>
    getDefaultOptionId('square'),
  )
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [rows, setRows] = useState<ContentListRow[]>([])
  const [total, setTotal] = useState<number | null>(null)
  const [hasNextPage, setHasNextPage] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [activeRow, setActiveRow] = useState<ContentListRow | null>(null)
  const [detail, setDetail] = useState<PublishedContentDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<ContentListRow | null>(null)
  const [deleteReason, setDeleteReason] = useState('')
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)

  const currentModule = useMemo(
    () => ADMIN_CONTENT_MODULES.find((item) => item.key === moduleKey) ?? null,
    [moduleKey],
  )

  const activeOption = useMemo(
    () =>
      getContentOptionById(activeOptionId) ??
      getContentOptionById(getDefaultOptionId(moduleKey)) ??
      null,
    [activeOptionId, moduleKey],
  )

  const detailImages = useMemo(() => parseImgUrls(detail?.imgUrls), [detail?.imgUrls])

  const detailType = useMemo(() => {
    if (typeof detail?.type === 'number') return detail.type
    if (typeof activeRow?.type === 'number') return activeRow.type
    if (typeof activeOption?.type === 'number') return activeOption.type
    return null
  }, [activeOption?.type, activeRow?.type, detail?.type])

  const detailChannelId = detail?.channelId ?? activeRow?.channelId ?? activeOption?.channelId ?? null
  const detailChannelName = detail?.channelName ?? activeRow?.channelName ?? null
  const detailCoverUrl = detail?.coverUrl ?? activeRow?.coverUrl ?? null
  const detailTitle = detail?.title?.trim() || activeRow?.title?.trim() || '未命名内容'
  const detailAuthor =
    detail?.userDTO?.nickName?.trim() ||
    activeRow?.userDTO?.nickName?.trim() ||
    '-'

  const loadList = useCallback(async () => {
    if (!activeOption) return

    setLoading(true)
    setError(null)

    try {
      const targetStart = (pageNum - 1) * pageSize
      const targetEnd = pageNum * pageSize
      const needCount = targetEnd + 1
      const matchedRows: ContentListRow[] = []
      const seenBlogIds = new Set<number>()
      let rawPageNum = 1
      let exhausted = false

      while (
        !exhausted &&
        matchedRows.length < needCount &&
        rawPageNum <= MAX_RAW_FETCH_PAGES
      ) {
        let rawData:
          | {
              code?: number
              message?: string
              data?: ContentListRow[]
              total?: number | null
            }
          | undefined

        if (activeOption.query.mode === 'all') {
          const resp = await getSquareContentList({
            pageNum: rawPageNum,
            pageSize: RAW_FETCH_BATCH_SIZE,
          })
          rawData = resp.data
          ensureApiSuccess(rawData, '内容列表加载失败，请稍后重试')
        } else if (activeOption.query.mode === 'type') {
          try {
            const resp = await getContentListByType({
              type: activeOption.query.type,
              pageNum: rawPageNum,
              pageSize: RAW_FETCH_BATCH_SIZE,
            })
            rawData = resp.data
            ensureApiSuccess(rawData, '内容列表加载失败，请稍后重试')
          } catch (errorByType) {
            if (
              activeOption.moduleKey === 'square' &&
              (activeOption.query.type === 1 || activeOption.query.type === 2)
            ) {
              const resp = await getSquareContentList({
                pageNum: rawPageNum,
                pageSize: RAW_FETCH_BATCH_SIZE,
              })
              rawData = resp.data
              ensureApiSuccess(rawData, '广场内容列表加载失败，请稍后重试')
            } else {
              throw errorByType
            }
          }
        } else {
          const resp = await getContentListByChannel({
            channelId: activeOption.query.channelId,
            pageNum: rawPageNum,
            pageSize: RAW_FETCH_BATCH_SIZE,
          })
          rawData = resp.data
          ensureApiSuccess(rawData, '频道内容列表加载失败，请稍后重试')
        }

        const rawRows = Array.isArray(rawData?.data) ? rawData.data : []

        for (const row of rawRows) {
          if (seenBlogIds.has(row.blogId)) continue
          if (!matchesActiveOption(row, activeOption)) continue
          seenBlogIds.add(row.blogId)
          matchedRows.push(row)
        }

        const rawTotal =
          typeof rawData?.total === 'number' ? rawData.total : null

        if (
          rawRows.length < RAW_FETCH_BATCH_SIZE ||
          (rawTotal !== null && rawPageNum * RAW_FETCH_BATCH_SIZE >= rawTotal)
        ) {
          exhausted = true
        } else {
          rawPageNum += 1
        }
      }

      const nextRows = matchedRows.slice(targetStart, targetEnd)
      const nextHasPage =
        exhausted ? matchedRows.length > targetEnd : matchedRows.length > targetEnd
      const nextTotal = exhausted ? matchedRows.length : null

      setRows(nextRows)
      setTotal(nextTotal)
      setHasNextPage(nextHasPage)
    } catch (errorLoad) {
      setError(getErrorMessage(errorLoad, '内容列表加载失败，请稍后重试'))
      setRows([])
      setTotal(null)
      setHasNextPage(false)
    } finally {
      setLoading(false)
    }
  }, [activeOption, pageNum, pageSize])

  useEffect(() => {
    void loadList()
  }, [loadList])

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(null), 3000)
    return () => window.clearTimeout(timer)
  }, [toast])

  const changeModule = useCallback((nextModule: AdminContentModuleKey) => {
    setModuleKey(nextModule)
    setActiveOptionId(getDefaultOptionId(nextModule))
    setPageNum(1)
    setRows([])
    setError(null)
    setDrawerOpen(false)
    setActiveRow(null)
    setDetail(null)
    setDetailError(null)
    setDeleteDialogOpen(false)
    setDeleteTarget(null)
    setDeleteReason('')
    setDeleteError(null)
  }, [])

  const changeOption = useCallback((optionId: string) => {
    setActiveOptionId(optionId)
    setPageNum(1)
    setError(null)
    setDrawerOpen(false)
    setActiveRow(null)
    setDetail(null)
    setDetailError(null)
  }, [])

  const closeDrawer = useCallback(() => {
    setDrawerOpen(false)
    setActiveRow(null)
    setDetail(null)
    setDetailError(null)
    setDetailLoading(false)
  }, [])

  const openDetail = useCallback(
    async (row: ContentListRow) => {
      const nextType =
        typeof row.type === 'number' ? row.type : activeOption?.type ?? null

      if (typeof nextType !== 'number') {
        setDrawerOpen(true)
        setActiveRow(row)
        setDetail(null)
        setDetailError('当前内容缺少详情类型，无法加载详情。')
        setDetailLoading(false)
        return
      }

      setActiveRow(row)
      setDrawerOpen(true)
      setDetail(null)
      setDetailError(null)
      setDetailLoading(true)

      try {
        const { data } = await getPublishedContentDetail(row.blogId, nextType)
        ensureApiSuccess(data, '内容详情加载失败，请稍后重试')
        if (!data?.data) {
          setDetailError(data?.message || '未查询到内容详情。')
          setDetail(null)
          return
        }
        setDetail(data.data)
      } catch (errorDetail) {
        setDetailError(getErrorMessage(errorDetail, '内容详情加载失败，请稍后重试'))
      } finally {
        setDetailLoading(false)
      }
    },
    [activeOption?.type],
  )

  const openDeleteDialog = useCallback((row: ContentListRow) => {
    setDeleteTarget(row)
    setDeleteReason('')
    setDeleteError(null)
    setDeleteDialogOpen(true)
  }, [])

  const closeDeleteDialog = useCallback(() => {
    if (deleting) return
    setDeleteDialogOpen(false)
    setDeleteTarget(null)
    setDeleteReason('')
    setDeleteError(null)
  }, [deleting])

  const submitDelete = useCallback(async () => {
    if (!deleteTarget) return

    const trimmedReason = deleteReason.trim()
    if (!trimmedReason) {
      setDeleteError('请填写删除意见后再提交。')
      return
    }

    setDeleting(true)
    setDeleteError(null)

    try {
      const resp = await deletePublishedContent(deleteTarget.blogId, trimmedReason)
      if (resp.data?.code === 200) {
        const shouldGoPrevPage = rows.length === 1 && pageNum > 1

        setToast('内容删除成功')
        setDeleteDialogOpen(false)
        setDeleteTarget(null)
        setDeleteReason('')

        if (activeRow?.blogId === deleteTarget.blogId) {
          closeDrawer()
          setActiveRow(null)
        }

        if (shouldGoPrevPage) {
          setPageNum((prev) => prev - 1)
        } else {
          await loadList()
        }
        return
      }

      setDeleteError(resp.data?.message || '删除失败，请稍后重试')
    } catch (errorDelete) {
      setDeleteError(getErrorMessage(errorDelete, '删除失败，请稍后重试'))
    } finally {
      setDeleting(false)
    }
  }, [activeRow?.blogId, closeDrawer, deleteReason, deleteTarget, loadList, pageNum, rows.length])

  const canGoPrev = pageNum > 1
  const canGoNext = hasNextPage

  const listSummary = useMemo(() => {
    if (total !== null) {
      return `共 ${total} 条，当前页 ${rows.length} 条`
    }
    return `当前第 ${pageNum} 页，共加载 ${rows.length} 条`
  }, [pageNum, rows.length, total])

  return (
    <>
      <section className="space-y-6">
        <header className="rounded-3xl border border-zinc-200 bg-white p-6 shadow-sm md:p-8">
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900">
            内容管理
          </h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-500">
            管理员可按广场、推荐、科普三大模块查看已发布内容，点击任意笔记、文章、视频均可查看详情，并支持填写删除意见后执行删除操作。
          </p>
        </header>

        <div className="flex flex-wrap gap-3">
          {ADMIN_CONTENT_MODULES.map((item) => (
            <ModuleButton
              key={item.key}
              label={item.label}
              description={item.description}
              active={moduleKey === item.key}
              onClick={() => changeModule(item.key)}
            />
          ))}
        </div>

        <div className="grid gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
          <aside className="space-y-4">
            <section className="rounded-3xl border border-zinc-200 bg-white p-5 shadow-sm">
              <div>
                <h2 className="text-base font-semibold text-zinc-900">
                  分类筛选
                </h2>
                <p className="mt-1 text-sm text-zinc-500">
                  {currentModule?.description || '请选择要管理的内容模块。'}
                </p>
              </div>

              {moduleKey === 'science' ? (
                <div className="mt-5 space-y-4">
                  {SCIENCE_CONTENT_GROUPS.map((group) => (
                    <section
                      key={group.id}
                      className="rounded-2xl border border-zinc-200 bg-zinc-50/70 p-4"
                    >
                      <h3 className="text-sm font-semibold text-zinc-900">
                        {group.parent}
                      </h3>
                      <div className="mt-3 flex flex-wrap gap-2">
                        {group.options.map((option) => {
                          const active = activeOption?.id === option.id
                          return (
                            <button
                              key={option.id}
                              type="button"
                              onClick={() => changeOption(option.id)}
                              className={cn(
                                'rounded-full border px-3 py-1.5 text-xs font-medium transition',
                                active
                                  ? 'border-zinc-900 bg-zinc-900 text-white'
                                  : 'border-zinc-200 bg-white text-zinc-700 hover:border-zinc-300 hover:bg-zinc-100',
                              )}
                            >
                              {option.shortLabel}
                            </button>
                          )
                        })}
                      </div>
                    </section>
                  ))}
                </div>
              ) : (
                <div className="mt-5 space-y-3">
                  {ADMIN_CONTENT_MODULES.find((item) => item.key === moduleKey) ? (
                    (() => {
                      const options =
                        moduleKey === 'square'
                          ? [
                              getContentOptionById('square-all'),
                              getContentOptionById('square-note'),
                              getContentOptionById('square-video'),
                            ].filter((item): item is AdminContentOption => !!item)
                          : [
                              getContentOptionById('recommend-video'),
                            ].filter((item): item is AdminContentOption => !!item)

                      return options.map((option) => (
                        <OptionButton
                          key={option.id}
                          option={option}
                          active={activeOption?.id === option.id}
                          onClick={() => changeOption(option.id)}
                        />
                      ))
                    })()
                  ) : null}
                </div>
              )}
            </section>

            <section className="rounded-3xl border border-zinc-200 bg-white p-5 shadow-sm">
              <h2 className="text-base font-semibold text-zinc-900">
                当前分类
              </h2>
              <div className="mt-4 space-y-3">
                <DetailField label="主模块" value={currentModule?.label || '-'} />
                <DetailField
                  label="子模块"
                  value={activeOption?.label || '未选择'}
                />
                <DetailField label="列表概况" value={listSummary} />
              </div>
            </section>
          </aside>

          <section className="rounded-3xl border border-zinc-200 bg-white shadow-sm">
            <div className="flex flex-col gap-4 border-b border-zinc-200 px-5 py-5 md:flex-row md:items-center md:justify-between md:px-6">
              <div>
                <h2 className="text-lg font-semibold text-zinc-900">
                  {activeOption?.label || '内容列表'}
                </h2>
                <p className="mt-1 text-sm text-zinc-500">
                  这里展示当前模块和子模块下的内容，支持查看详情与删除。
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <label className="flex items-center gap-2 text-sm text-zinc-500">
                  每页
                  <select
                    value={pageSize}
                    onChange={(e) => {
                      setPageSize(Number(e.target.value))
                      setPageNum(1)
                    }}
                    className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm text-zinc-900 outline-none transition focus:border-zinc-400 focus:ring-1 focus:ring-zinc-300"
                  >
                    {PAGE_SIZE_OPTIONS.map((size) => (
                      <option key={size} value={size}>
                        {size}
                      </option>
                    ))}
                  </select>
                  条
                </label>

                <button
                  type="button"
                  onClick={() => void loadList()}
                  disabled={loading}
                  className={cn(
                    'inline-flex items-center gap-2 rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm text-zinc-700 transition hover:bg-zinc-50',
                    loading && 'pointer-events-none opacity-60',
                  )}
                >
                  <RefreshCw className={cn('size-4', loading && 'animate-spin')} />
                  刷新
                </button>
              </div>
            </div>

            {loading ? (
              <div className="px-6 py-12 text-center text-sm text-zinc-500">
                正在加载内容列表...
              </div>
            ) : error ? (
              <div className="px-6 py-12 text-center text-sm text-red-500">
                {error}
              </div>
            ) : rows.length === 0 ? (
              <div className="px-6 py-12 text-center text-sm text-zinc-500">
                当前分类下暂无内容。
              </div>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[1100px] border-collapse">
                    <thead>
                      <tr className="border-b border-zinc-200 bg-zinc-50/80">
                        <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">
                          ID
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">
                          预览
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">
                          标题 / 分类
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">
                          模块 / 类型
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">
                          作者
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">
                          状态 / 点赞
                        </th>
                        <th className="sticky right-0 bg-zinc-50/80 px-4 py-3 text-right text-xs font-medium text-zinc-500">
                          操作
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-100">
                      {rows.map((row) => {
                        const rowType = row.type ?? activeOption?.type ?? null
                        const rowChannelId = row.channelId ?? activeOption?.channelId ?? null
                        const rowCategory = resolveContentCategoryLabel(
                          rowType,
                          rowChannelId,
                          row.channelName,
                        )

                        return (
                          <tr
                            key={row.blogId}
                            className="transition-colors hover:bg-zinc-50/70"
                          >
                            <td className="px-4 py-4 text-sm tabular-nums text-zinc-700">
                              {row.blogId}
                            </td>
                            <td className="px-4 py-4">
                              <button
                                type="button"
                                onClick={() => void openDetail(row)}
                                className="rounded-xl outline-none transition hover:scale-[1.02] focus-visible:ring-2 focus-visible:ring-zinc-300"
                              >
                                <PreviewThumb coverUrl={row.coverUrl} type={rowType} />
                              </button>
                            </td>
                            <td className="max-w-[320px] px-4 py-4">
                              <button
                                type="button"
                                onClick={() => void openDetail(row)}
                                className="text-left"
                              >
                                <p className="line-clamp-2 text-sm font-semibold leading-6 text-zinc-900 transition hover:text-zinc-700">
                                  {row.title?.trim() || '未命名内容'}
                                </p>
                              </button>
                              <p className="mt-1 text-xs text-zinc-500">
                                {resolveContentParentLabel(rowType, rowChannelId)} / {rowCategory}
                              </p>
                            </td>
                            <td className="px-4 py-4">
                              <p className="text-sm font-medium text-zinc-900">
                                {resolveContentModuleLabel(rowType)}
                              </p>
                              <p className="mt-1 text-xs text-zinc-500">
                                {resolveContentTypeLabel(rowType)}
                              </p>
                            </td>
                            <td className="px-4 py-4 text-sm text-zinc-700">
                              {row.userDTO?.nickName?.trim() || '-'}
                            </td>
                            <td className="px-4 py-4">
                              <div className="flex flex-col items-start gap-2">
                                <StatusBadge status={row.status} />
                                <span className="text-xs text-zinc-500">
                                  点赞：{row.likeTotal ?? 0}
                                </span>
                              </div>
                            </td>
                            <td className="sticky right-0 bg-white px-4 py-4 text-right">
                              <div className="flex justify-end gap-2">
                                <button
                                  type="button"
                                  onClick={() => void openDetail(row)}
                                  className="inline-flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-3 py-2 text-xs font-medium text-zinc-700 transition hover:bg-zinc-50"
                                >
                                  <Eye className="size-3.5" />
                                  查看
                                </button>
                                <button
                                  type="button"
                                  onClick={() => openDeleteDialog(row)}
                                  className="inline-flex items-center gap-1 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs font-medium text-red-600 transition hover:bg-red-100"
                                >
                                  <Trash2 className="size-3.5" />
                                  删除
                                </button>
                              </div>
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>

                <div className="flex flex-col gap-3 border-t border-zinc-200 px-5 py-4 text-sm md:flex-row md:items-center md:justify-between md:px-6">
                  <p className="text-zinc-500">{listSummary}</p>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      disabled={!canGoPrev}
                      onClick={() => setPageNum((prev) => Math.max(prev - 1, 1))}
                      className={cn(
                        'rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm text-zinc-700 transition hover:bg-zinc-50',
                        !canGoPrev && 'pointer-events-none opacity-50',
                      )}
                    >
                      上一页
                    </button>
                    <span className="min-w-16 text-center text-zinc-600">
                      第 {pageNum} 页
                    </span>
                    <button
                      type="button"
                      disabled={!canGoNext}
                      onClick={() => setPageNum((prev) => prev + 1)}
                      className={cn(
                        'rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm text-zinc-700 transition hover:bg-zinc-50',
                        !canGoNext && 'pointer-events-none opacity-50',
                      )}
                    >
                      下一页
                    </button>
                  </div>
                </div>
              </>
            )}
          </section>
        </div>
      </section>

      {drawerOpen ? (
        <div className="fixed inset-0 z-50 flex justify-end">
          <button
            type="button"
            className="absolute inset-0 bg-zinc-950/40"
            onClick={closeDrawer}
            aria-label="关闭内容详情"
          />

          <aside className="relative z-10 flex h-full w-full max-w-4xl flex-col border-l border-zinc-200 bg-zinc-50 shadow-2xl">
            <div className="flex items-center justify-between border-b border-zinc-200 bg-white px-6 py-4">
              <div>
                <h2 className="text-lg font-semibold text-zinc-900">内容详情</h2>
                <p className="mt-1 text-xs text-zinc-500 tabular-nums">
                  Blog ID：{activeRow?.blogId ?? detail?.blogId ?? detail?.noteId ?? '-'}
                </p>
              </div>

              <button
                type="button"
                onClick={closeDrawer}
                className="rounded-lg p-2 text-zinc-500 transition hover:bg-zinc-100 hover:text-zinc-700"
              >
                <X className="size-4" />
              </button>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
              {detailLoading ? (
                <div className="rounded-2xl border border-zinc-200 bg-white px-6 py-12 text-center text-sm text-zinc-500">
                  正在加载内容详情...
                </div>
              ) : detailError ? (
                <div className="rounded-2xl border border-zinc-200 bg-white px-6 py-12 text-center text-sm text-red-500">
                  {detailError}
                </div>
              ) : (
                <div className="space-y-6">
                  <section className="rounded-3xl border border-zinc-200 bg-white p-5 shadow-sm">
                    <h3 className="text-xl font-semibold tracking-tight text-zinc-900">
                      {detailTitle}
                    </h3>
                    <p className="mt-2 text-sm leading-6 text-zinc-500">
                      点击删除前，请先确认该内容的正文、图片、视频和所属模块信息。
                    </p>

                    <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                      <DetailField
                        label="主模块"
                        value={resolveContentModuleLabel(detailType)}
                      />
                      <DetailField
                        label="内容类型"
                        value={resolveContentTypeLabel(detailType)}
                      />
                      <DetailField
                        label="所属分类"
                        value={resolveContentCategoryLabel(
                          detailType,
                          detailChannelId,
                          detailChannelName,
                        )}
                      />
                      <DetailField label="作者" value={detailAuthor} />
                      <DetailField
                        label="状态"
                        value={resolveStatusMeta(detail?.status ?? activeRow?.status).label}
                      />
                      <DetailField
                        label="点赞数"
                        value={String(detail?.likeTotal ?? activeRow?.likeTotal ?? 0)}
                      />
                    </div>
                  </section>

                  {detailCoverUrl ? (
                    <section className="rounded-3xl border border-zinc-200 bg-white p-5 shadow-sm">
                      <h3 className="text-sm font-semibold text-zinc-900">封面</h3>
                      <img
                        src={detailCoverUrl}
                        alt=""
                        className="mt-4 max-h-[420px] w-full rounded-2xl border border-zinc-200 object-cover"
                      />
                    </section>
                  ) : null}

                  {detail?.videoUrl ? (
                    <section className="rounded-3xl border border-zinc-200 bg-white p-5 shadow-sm">
                      <h3 className="text-sm font-semibold text-zinc-900">视频内容</h3>
                      <video
                        controls
                        src={detail.videoUrl}
                        className="mt-4 w-full rounded-2xl border border-zinc-200 bg-black"
                      />
                    </section>
                  ) : null}

                  {detailImages.length > 0 ? (
                    <section className="rounded-3xl border border-zinc-200 bg-white p-5 shadow-sm">
                      <h3 className="text-sm font-semibold text-zinc-900">图片内容</h3>
                      <div className="mt-4 grid grid-cols-2 gap-3 lg:grid-cols-3">
                        {detailImages.map((url) => (
                          <img
                            key={url}
                            src={url}
                            alt=""
                            className="aspect-square w-full rounded-2xl border border-zinc-200 object-cover"
                          />
                        ))}
                      </div>
                    </section>
                  ) : null}

                  {detail?.content?.trim() ? (
                    <section className="rounded-3xl border border-zinc-200 bg-white p-5 shadow-sm">
                      <h3 className="text-sm font-semibold text-zinc-900">正文内容</h3>
                      <div className="mt-4">
                        <ContentBody content={detail.content} />
                      </div>
                    </section>
                  ) : null}
                </div>
              )}
            </div>

            <div className="flex items-center justify-end gap-3 border-t border-zinc-200 bg-white px-6 py-4">
              <button
                type="button"
                onClick={closeDrawer}
                className="rounded-lg border border-zinc-200 bg-white px-4 py-2 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50"
              >
                关闭
              </button>
              <button
                type="button"
                disabled={!activeRow}
                onClick={() => {
                  if (activeRow) openDeleteDialog(activeRow)
                }}
                className={cn(
                  'rounded-lg border border-red-200 bg-red-50 px-4 py-2 text-sm font-medium text-red-600 transition hover:bg-red-100',
                  !activeRow && 'pointer-events-none opacity-50',
                )}
              >
                删除当前内容
              </button>
            </div>
          </aside>
        </div>
      ) : null}

      {deleteDialogOpen ? (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
          <button
            type="button"
            className="absolute inset-0 bg-zinc-950/45"
            onClick={closeDeleteDialog}
            aria-label="关闭删除弹窗"
          />

          <div className="relative z-10 w-full max-w-xl rounded-3xl border border-zinc-200 bg-white shadow-2xl">
            <div className="border-b border-zinc-200 px-6 py-5">
              <h3 className="text-lg font-semibold text-zinc-900">
                删除内容
              </h3>
              <p className="mt-1.5 text-sm leading-6 text-zinc-500">
                删除前请填写明确的删除意见，提交后将调用删除接口执行操作。
              </p>
            </div>

            <div className="space-y-4 px-6 py-5">
              <div className="rounded-2xl border border-zinc-200 bg-zinc-50/70 p-4">
                <p className="text-xs uppercase tracking-wide text-zinc-400">
                  删除目标
                </p>
                <p className="mt-2 text-sm font-semibold text-zinc-900">
                  {deleteTarget?.title?.trim() || activeRow?.title?.trim() || '未命名内容'}
                </p>
                <p className="mt-1 text-xs text-zinc-500">
                  Blog ID：{deleteTarget?.blogId ?? '-'} ·{' '}
                  {resolveContentTypeLabel(deleteTarget?.type ?? activeOption?.type)}
                </p>
              </div>

              <div>
                <label
                  htmlFor="delete-reason"
                  className="block text-sm font-medium text-zinc-700"
                >
                  删除意见
                </label>
                <textarea
                  id="delete-reason"
                  value={deleteReason}
                  onChange={(e) => {
                    setDeleteReason(e.target.value)
                    if (deleteError) setDeleteError(null)
                  }}
                  placeholder="请填写删除原因，例如：内容违规、信息错误、重复发布或运营下线等。"
                  className="mt-2 min-h-36 w-full resize-y rounded-2xl border border-zinc-200 px-4 py-3 text-sm leading-6 text-zinc-900 outline-none transition focus:border-zinc-400 focus:ring-2 focus:ring-zinc-200"
                />
                <p className="mt-2 text-xs text-zinc-500">
                  删除意见将随删除请求一起提交，便于后端保留管理操作记录。
                </p>
              </div>

              {deleteError ? (
                <p className="text-sm text-red-500">{deleteError}</p>
              ) : null}
            </div>

            <div className="flex items-center justify-end gap-3 border-t border-zinc-200 px-6 py-4">
              <button
                type="button"
                disabled={deleting}
                onClick={closeDeleteDialog}
                className={cn(
                  'rounded-lg border border-zinc-200 bg-white px-4 py-2 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50',
                  deleting && 'pointer-events-none opacity-60',
                )}
              >
                取消
              </button>
              <button
                type="button"
                disabled={deleting}
                onClick={() => void submitDelete()}
                className={cn(
                  'rounded-lg border border-red-600 bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700',
                  deleting && 'pointer-events-none opacity-60',
                )}
              >
                {deleting ? '删除中...' : '确认删除'}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {toast ? (
        <div className="fixed bottom-8 left-1/2 z-[70] -translate-x-1/2 rounded-xl border border-zinc-800 bg-zinc-900 px-4 py-2.5 text-sm text-white shadow-lg">
          {toast}
        </div>
      ) : null}
    </>
  )
}
