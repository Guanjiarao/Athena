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
import { replacePendingEmbeddedImagesInHtml } from '@/lib/article-content'
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

const PLACEHOLDER_TOPIC_ID = 9001

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
    const timer = window.setTimeout(() => setToast(null), 3500)
    return () => window.clearTimeout(timer)
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
      const url = await uploadMediaFile(file)
      setCoverUrl(url)
    } catch (err) {
      const msg = err instanceof Error ? err.message : '封面上传失败，请重试'
      setFormError(msg)
    } finally {
      setCoverUploading(false)
    }
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)

    const trimmedTitle = title.trim()
    const child = activeGroup.children[childIndex]
    const currentCoverUrl = coverUrl

    if (!trimmedTitle) {
      setFormError('请填写文章标题')
      return
    }
    if (!currentCoverUrl) {
      setFormError('请先上传封面图')
      return
    }
    if (isRichTextEmpty(content)) {
      setFormError('请填写正文内容')
      return
    }
    if (!child) {
      setFormError('请选择具体板块')
      return
    }
    if (!user?.token) {
      setFormError('登录状态已过期，请重新登录')
      return
    }

    setSubmitting(true)
    try {
      const { html: processedContent, failed } =
        await replacePendingEmbeddedImagesInHtml(content)

      if (failed > 0) {
        throw new Error('正文中的图片上传失败，请重新粘贴后再提交')
      }

      if (processedContent !== content) {
        setContent(processedContent)
      }

      const payload = {
        title: trimmedTitle,
        topicId: PLACEHOLDER_TOPIC_ID,
        topicName: child.name,
        isTop: false,
        type: child.type,
        coverUrl: currentCoverUrl,
        videoUrl: '',
        visible: 1,
        content: processedContent,
        channelId: child.channelId,
        channelName: activeGroup.parent,
      }

      const { data } = await submitOfficialBlog(payload)
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
        setFormError(err instanceof Error ? err.message : '网络异常，请稍后重试')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="relative mx-auto max-w-3xl">
      <header className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 md:text-[1.65rem]">
          发布公众号文章
        </h1>
        <p className="mt-2 text-sm text-zinc-500">
          请选择文章所属板块，并在正文中直接编辑图文内容。
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
                  {CATEGORY_MAP.map((group, index) => (
                    <option key={group.parent} value={index}>
                      {group.parent}
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
                  {activeGroup.children.map((item, index) => (
                    <option
                      key={`${item.name}-${item.type}-${item.channelId}`}
                      value={index}
                    >
                      {item.name}
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
                    {coverUploading ? '上传中...' : '点击可更换封面'}
                  </span>
                </>
              ) : (
                <span className="text-sm text-zinc-500">
                  {coverUploading ? '上传中...' : '点击上传文章封面'}
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
                placeholder="在此撰写图文正文，支持直接粘贴图片。"
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
            {submitting ? '提交中...' : '确认发布'}
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
