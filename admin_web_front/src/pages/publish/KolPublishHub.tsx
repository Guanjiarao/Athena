import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { isAxiosError } from 'axios'
import { motion } from 'framer-motion'
import { ImagePlus, UploadCloud, Video } from 'lucide-react'

import { submitOfficialBlog } from '@/api/blogSubmit'
import { uploadMediaFile } from '@/api/fileUpload'
import { useAuth } from '@/contexts/AuthContext'
import { cn } from '@/lib/utils'

export function KolPublishHub() {
  const { user } = useAuth()
  const coverInputRef = useRef<HTMLInputElement>(null)
  const videoInputRef = useRef<HTMLInputElement>(null)
  const [title, setTitle] = useState('')
  const [coverUrl, setCoverUrl] = useState('')
  const [videoUrl, setVideoUrl] = useState('')
  const [content, setContent] = useState('')
  const [uploadingCover, setUploadingCover] = useState(false)
  const [uploadingVideo, setUploadingVideo] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(null), 3000)
    return () => window.clearTimeout(timer)
  }, [toast])

  const resetForm = () => {
    setTitle('')
    setCoverUrl('')
    setVideoUrl('')
    setContent('')
  }

  const handlePickCover = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setError(null)
    setUploadingCover(true)
    try {
      const uploaded = await uploadMediaFile(file)
      setCoverUrl(uploaded)
    } catch (e1) {
      setError(e1 instanceof Error ? e1.message : '封面上传失败，请重试')
    } finally {
      setUploadingCover(false)
    }
  }

  const handlePickVideo = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setError(null)
    setUploadingVideo(true)
    try {
      const uploaded = await uploadMediaFile(file)
      setVideoUrl(uploaded)
    } catch (e1) {
      setError(e1 instanceof Error ? e1.message : '视频上传失败，请重试')
    } finally {
      setUploadingVideo(false)
    }
  }

  const handleSubmit = async () => {
    console.log('【Debug】1. 发布按钮已触发，进入发布函数！')
    setError(null)
    try {
      console.log('【Debug】2. 准备执行表单校验...')
      if (!title.trim()) {
        throw new Error('请填写视频标题')
      }
      if (!user?.token) {
        throw new Error('登录状态已过期，请重新登录')
      }
      if (!videoUrl) {
        throw new Error('请先上传视频')
      }
      if (!content.trim()) {
        throw new Error('请填写视频简介')
      }
      console.log('【Debug】3. 表单校验通过！准备构造请求数据...')
    } catch (err) {
      console.error('【Debug 致命错误】表单校验失败！错误详情：', err)
      setError(err instanceof Error ? err.message : '表单校验失败')
      return
    }

    const payload = {
      title: title.trim(),
      topicId: 0,
      topicName: '',
      isTop: false,
      type: 0,
      coverUrl: coverUrl.trim(),
      imgUrls: [],
      videoUrl,
      visible: 1,
      content: content.trim(),
      channelId: 0,
      channelName: '',
    }

    setSubmitting(true)
    try {
      console.log('【Debug】4. 准备发送网络请求，Payload 数据为：', payload)
      const { data } = await submitOfficialBlog(payload)
      if (data?.code === 200) {
        setToast('提交成功，请等待系统审核')
        resetForm()
        return
      }
      setError(
        typeof data?.message === 'string' && data.message
          ? data.message
          : '提交失败，请稍后重试',
      )
    } catch (e1) {
      console.error('【Debug 致命错误】请求发送时发生异常：', e1)
      if (isAxiosError(e1)) {
        const msg =
          (e1.response?.data as { message?: string } | undefined)?.message ||
          e1.message
        setError(msg || '网络异常，请稍后重试')
      } else {
        setError('网络异常，请稍后重试')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <div className="mx-auto w-full max-w-[900px] pb-32 pt-1">
        <div className="rounded-2xl border border-zinc-200/80 bg-white p-6 shadow-xl md:p-10">
          <div className="space-y-5">
            <div>
              <label htmlFor="kol-video-title" className="mb-1.5 block text-xs text-zinc-500">
                视频标题
              </label>
              <input
                id="kol-video-title"
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="请输入视频标题"
                className="w-full rounded-xl border border-zinc-200 bg-white px-4 py-3 text-sm text-zinc-900 placeholder:text-zinc-400 shadow-sm outline-none transition focus:border-zinc-400 focus:ring-1 focus:ring-zinc-300"
              />
            </div>

            <input
              ref={videoInputRef}
              type="file"
              accept="video/*"
              className="sr-only"
              onChange={handlePickVideo}
            />
            <button
              type="button"
              onClick={() => videoInputRef.current?.click()}
              disabled={uploadingVideo}
              className={cn(
                'flex h-[220px] w-full flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-zinc-300 bg-zinc-50 text-zinc-600 transition hover:border-zinc-500',
                uploadingVideo && 'pointer-events-none opacity-60',
              )}
            >
              <UploadCloud className="size-10 text-zinc-400" />
              <span className="flex items-center gap-2 text-sm">
                <Video className="size-4 text-zinc-400" />
                {uploadingVideo ? '上传中...' : '上传视频文件'}
              </span>
              {videoUrl ? <span className="text-xs text-zinc-500">已上传视频</span> : null}
            </button>

            <div>
              <input
                ref={coverInputRef}
                type="file"
                accept="image/*"
                className="sr-only"
                onChange={handlePickCover}
              />
              <button
                type="button"
                onClick={() => coverInputRef.current?.click()}
                disabled={uploadingCover}
                className={cn(
                  'flex w-full items-center justify-center gap-2 rounded-lg border border-dashed border-zinc-300 bg-zinc-50 px-4 py-4 text-sm text-zinc-600 transition hover:border-zinc-500',
                  uploadingCover && 'pointer-events-none opacity-60',
                )}
              >
                <ImagePlus className="size-4" aria-hidden />
                {uploadingCover ? '封面上传中...' : '上传封面图（可选）'}
              </button>
              {coverUrl ? (
                <img
                  src={coverUrl}
                  alt=""
                  className="mt-3 max-h-40 rounded-md border border-zinc-200 object-cover"
                />
              ) : null}
            </div>

            <div>
              <label htmlFor="kol-video-content" className="mb-1.5 block text-xs text-zinc-500">
                视频简介/内容
              </label>
              <textarea
                id="kol-video-content"
                value={content}
                onChange={(e) => setContent(e.target.value)}
                rows={6}
                placeholder="请输入视频简介内容"
                className="w-full resize-y rounded-xl border border-zinc-200 bg-white px-4 py-3.5 text-sm leading-relaxed text-zinc-900 placeholder:text-zinc-400 shadow-sm outline-none transition focus:border-zinc-400 focus:ring-1 focus:ring-zinc-300"
              />
            </div>
          </div>

          {error ? <p className="mt-5 text-xs text-red-500">{error}</p> : null}
        </div>
      </div>

      <motion.button
        type="button"
        whileTap={{ scale: 0.94 }}
        transition={{ type: 'spring', stiffness: 520, damping: 28 }}
        className={cn(
          'fixed bottom-8 right-8 z-40 rounded-xl bg-zinc-900 px-6 py-3 text-sm font-semibold text-white shadow-sm',
          'hover:bg-zinc-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900',
          submitting && 'pointer-events-none opacity-60',
        )}
        onClick={handleSubmit}
      >
        {submitting ? '提交中...' : '提交'}
      </motion.button>

      {toast ? (
        <div className="fixed bottom-24 left-1/2 z-50 -translate-x-1/2 rounded-lg border border-zinc-800 bg-zinc-900 px-4 py-3 text-sm text-white shadow-lg">
          {toast}
        </div>
      ) : null}
    </>
  )
}
