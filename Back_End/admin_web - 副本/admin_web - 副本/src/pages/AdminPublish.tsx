import {
  type ChangeEvent,
  type FormEvent,
  lazy,
  Suspense,
  useEffect,
  useRef,
  useState,
} from 'react'
import { isAxiosError } from 'axios'

import { submitOfficialBlog } from '@/api/blogSubmit'
import { uploadMediaFile } from '@/api/fileUpload'
import { useAuth } from '@/contexts/AuthContext'
import { CATEGORY_MAP } from '@/lib/category-map'
import { cn } from '@/lib/utils'

const ArticleRichEditor = lazy(async () => {
  const m = await import('@/components/admin/ArticleRichEditor')
  return { default: m.ArticleRichEditor }
})

function isRichTextEmpty(html: string): boolean {
  const div = document.createElement('div')
  div.innerHTML = html
  const text = (div.textContent || '')
    .replace(/\u00a0/g, ' ')
    .replace(/\n/g, '')
    .trim()
  return text.length === 0
}

/** 与 AuthContext 中 `STORAGE_KEY` 保持一致 */
const AUTH_SESSION_STORAGE_KEY = 'athena-auth-session'

function parsePositiveUserId(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
    return Math.trunc(value)
  }
  if (typeof value === 'string' && value.trim()) {
    const n = Number(value.trim())
    if (!Number.isNaN(n) && n > 0) return Math.trunc(n)
  }
  return null
}

/**
 * 从当前登录态与会话存储解析真实 userId，不使用写死占位。
 * 顺序：AuthContext → localStorage 会话 JSON → localStorage `userId` → sessionStorage `userId`
 */
function resolvePublishUserId(user: {
  userId?: number
} | null): number | null {
  const fromCtx = parsePositiveUserId(user?.userId)
  if (fromCtx !== null) return fromCtx

  try {
    const raw = localStorage.getItem(AUTH_SESSION_STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as { userId?: unknown }
      const n = parsePositiveUserId(parsed?.userId)
      if (n !== null) return n
    }
  } catch {
    /* ignore */
  }

  const ls = parsePositiveUserId(localStorage.getItem('userId'))
  if (ls !== null) return ls

  const ss = parsePositiveUserId(sessionStorage.getItem('userId'))
  if (ss !== null) return ss

  return null
}

/** 从富文本 HTML 中提取所有 <img> 的 src，供移动端列表缩略图 */
function extractImgUrlsFromHtml(htmlContent: string): string[] {
  const regex =
    /<img\b[^>]*?\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/gi
  const urls: string[] = []
  const seen = new Set<string>()
  let match: RegExpExecArray | null
  while ((match = regex.exec(htmlContent)) !== null) {
    const raw = (match[1] ?? match[2] ?? match[3] ?? '').trim()
    if (!raw || seen.has(raw)) continue
    seen.add(raw)
    urls.push(raw)
  }
  return urls
}

const PLACEHOLDER_TOPIC_ID = 9001

/** 管理员发布公众号文章页 */
export function AdminPublishPage() {
  const { user } = useAuth()
  const coverInputRef = useRef<HTMLInputElement>(null)
  const [title, setTitle] = useState('')
  const [parentIndex, setParentIndex] = useState(0)
  const [childIndex, setChildIndex] = useState(0)
  const [coverUrl, setCoverUrl] = useState<string | null>(null)
  const [coverUploading, setCoverUploading] = useState(false)
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const activeGroup = CATEGORY_MAP[parentIndex] ?? CATEGORY_MAP[0]

  useEffect(() => {
    if (!toast) return
    const t = window.setTimeout(() => setToast(null), 3500)
    return () => window.clearTimeout(t)
  }, [toast])

  const handleParentChange = (e: ChangeEvent<HTMLSelectElement>) => {
    const next = Number(e.target.value)
    if (Number.isNaN(next)) return
    setParentIndex(next)
    setChildIndex(0)
  }

  const handleChildChange = (e: ChangeEvent<HTMLSelectElement>) => {
    const next = Number(e.target.value)
    if (Number.isNaN(next)) return
    setChildIndex(next)
  }

  const openCoverPicker = () => {
    if (coverUploading) return
    setFormError(null)
    coverInputRef.current?.click()
  }

  const handleCoverFileChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return

    setFormError(null)
    setCoverUploading(true)
    try {
      const url = await uploadMediaFile(file, user?.token)
      setCoverUrl(url)
    } catch (err) {
      const msg =
        err instanceof Error ? err.message : '封面上传失败，请重试'
      setFormError(msg)
    } finally {
      setCoverUploading(false)
    }
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)

    const trimmedTitle = title.trim()
    if (!trimmedTitle) {
      setFormError('请填写文章标题')
      return
    }
    if (!coverUrl) {
      setFormError('请先上传封面图')
      return
    }
    if (isRichTextEmpty(content)) {
      setFormError('请填写正文内容')
      return
    }

    const child = activeGroup.children[childIndex]
    if (!child) {
      setFormError('请选择具体板块')
      return
    }

    const userId = resolvePublishUserId(user)
    if (userId === null) {
      setFormError('无法获取当前账号的用户 ID，请重新登录')
      return
    }

    const imgUrls = extractImgUrlsFromHtml(content)

    const payload = {
      userId,
      title: trimmedTitle,
      topicId: PLACEHOLDER_TOPIC_ID,
      topicName: child.name,
      isTop: false,
      type: child.type,
      coverUrl,
      imgUrls,
      videoUrl: '',
      visible: 1,
      content,
      channelId: child.channelId,
      channelName: activeGroup.parent,
    }

    setSubmitting(true)
    try {
      const { data } = await submitOfficialBlog(payload, user?.token)
      if (data?.code === 200) {
        setToast('提交成功，请等待系统审核')
        setTitle('')
        setParentIndex(0)
        setChildIndex(0)
        setCoverUrl(null)
        setContent('')
        return
      }
      const msg =
        typeof data?.message === 'string' && data.message
          ? data.message
          : '发布失败，请稍后重试'
      setFormError(msg)
    } catch (err) {
      if (isAxiosError(err)) {
        const body = err.response?.data as { message?: string } | undefined
        const msg =
          body && typeof body.message === 'string' && body.message
            ? body.message
            : err.message || '网络异常，请稍后重试'
        setFormError(msg)
      } else {
        setFormError('网络异常，请稍后重试')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="relative mx-auto max-w-3xl">
      <header className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 md:text-[1.65rem]">
          发布官方公众号文章
        </h1>
        <p className="mt-2 text-sm text-zinc-500">
          请严格选择文章所属的年龄段或专区板块
        </p>
      </header>

      <form
        onSubmit={handleSubmit}
        className="rounded-xl border border-zinc-200 bg-white p-6 shadow-sm md:p-8"
      >
        <div className="space-y-8">
          <div>
            <label htmlFor="article-title" className="sr-only">
              文章标题
            </label>
            <input
              id="article-title"
              name="title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="文章标题"
              className="w-full border-0 border-b border-zinc-200 bg-transparent px-0 py-3 text-2xl font-semibold tracking-tight text-zinc-900 placeholder:text-zinc-300 focus:border-zinc-400 focus:outline-none focus:ring-0 md:text-[1.65rem]"
            />
          </div>

          <div>
            <p className="mb-3 text-xs font-medium uppercase tracking-wider text-zinc-400">
              板块
            </p>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor="category-parent"
                  className="text-xs text-zinc-500"
                >
                  所属大类
                </label>
                <select
                  id="category-parent"
                  name="categoryParent"
                  value={parentIndex}
                  onChange={handleParentChange}
                  className="w-full cursor-pointer rounded-lg border border-zinc-200 bg-white px-3 py-2.5 text-sm text-zinc-900 shadow-sm outline-none transition focus:border-zinc-400 focus:ring-1 focus:ring-zinc-300"
                >
                  {CATEGORY_MAP.map((g, i) => (
                    <option key={g.parent} value={i}>
                      {g.parent}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor="category-child"
                  className="text-xs text-zinc-500"
                >
                  具体板块
                </label>
                <select
                  id="category-child"
                  name="categoryChild"
                  value={childIndex}
                  onChange={handleChildChange}
                  className="w-full cursor-pointer rounded-lg border border-zinc-200 bg-white px-3 py-2.5 text-sm text-zinc-900 shadow-sm outline-none transition focus:border-zinc-400 focus:ring-1 focus:ring-zinc-300"
                >
                  {activeGroup.children.map((c, i) => (
                    <option key={`${c.name}-${c.type}-${c.channelId}`} value={i}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          <div>
            <p className="mb-3 text-xs font-medium uppercase tracking-wider text-zinc-400">
              封面
            </p>
            <input
              ref={coverInputRef}
              type="file"
              accept="image/*"
              className="sr-only"
              aria-hidden
              tabIndex={-1}
              onChange={handleCoverFileChange}
            />
            <button
              type="button"
              onClick={openCoverPicker}
              disabled={coverUploading}
              className={cn(
                'flex w-full flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-zinc-300 bg-zinc-50/50 px-4 py-12 text-center transition hover:border-zinc-400 hover:bg-zinc-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900',
                coverUploading && 'pointer-events-none opacity-60',
              )}
            >
              {coverUrl ? (
                <>
                  <img
                    src={coverUrl}
                    alt=""
                    className="max-h-40 w-full max-w-sm rounded-md border border-zinc-200 object-cover"
                  />
                  <span className="text-xs text-zinc-500">
                    {coverUploading ? '上传中…' : '点击可更换封面'}
                  </span>
                </>
              ) : (
                <span className="text-sm text-zinc-500">
                  {coverUploading
                    ? '上传中…'
                    : '点击上传文章封面 （Cover）'}
                </span>
              )}
            </button>
          </div>

          <div>
            <p className="mb-3 text-xs font-medium uppercase tracking-wider text-zinc-400">
              正文
            </p>
            <Suspense
              fallback={
                <div
                  className="min-h-[456px] rounded-lg border border-zinc-200 bg-zinc-50"
                  aria-hidden
                />
              }
            >
              <ArticleRichEditor
                value={content}
                onChange={setContent}
                placeholder="在此撰写公众号长图文内容 （支持 HTML/Markdown）"
                token={user?.token}
                onImageUploadError={(msg) => setFormError(msg)}
              />
            </Suspense>
          </div>
        </div>

        {formError ? (
          <p
            className="mt-6 text-center text-xs leading-relaxed text-red-500"
            role="alert"
          >
            {formError}
          </p>
        ) : null}

        <div className="mt-6 flex justify-end border-t border-zinc-100 pt-6">
          <button
            type="submit"
            disabled={submitting}
            className={cn(
              'rounded-lg bg-zinc-900 px-6 py-2.5 text-sm font-medium text-white shadow-sm transition hover:bg-zinc-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900',
              submitting && 'pointer-events-none opacity-60',
            )}
          >
            {submitting ? '发布中...' : '确认发布'}
          </button>
        </div>
      </form>

      {toast ? (
        <div
          role="status"
          aria-live="polite"
          className="fixed bottom-8 left-1/2 z-50 max-w-[min(90vw,360px)] -translate-x-1/2 rounded-lg border border-zinc-800 bg-zinc-900 px-4 py-3 text-center text-sm font-medium text-white shadow-lg shadow-zinc-900/20"
        >
          {toast}
        </div>
      ) : null}
    </div>
  )
}
