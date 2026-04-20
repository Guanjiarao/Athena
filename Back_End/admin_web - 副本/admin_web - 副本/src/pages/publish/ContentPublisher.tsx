import { useCallback, useRef, useState, type DragEvent } from 'react'
import { motion } from 'framer-motion'
import { Video } from 'lucide-react'

import { cn } from '@/lib/utils'

type PublishMode = 'note' | 'video'

function useFileDrop(accept: string) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [over, setOver] = useState(false)

  const pickFiles = useCallback((list: FileList | null) => {
    if (!list?.length) return
    const f = list[0]
    if (!f) return
    setFile(f)
  }, [])

  const onDrop = useCallback(
    (e: DragEvent) => {
      e.preventDefault()
      setOver(false)
      pickFiles(e.dataTransfer.files)
    },
    [pickFiles],
  )

  const onDragOver = useCallback((e: DragEvent) => {
    e.preventDefault()
    setOver(true)
  }, [])

  const onDragLeave = useCallback(() => setOver(false), [])

  const openPicker = useCallback(() => inputRef.current?.click(), [])

  return {
    inputRef,
    file,
    over,
    onDrop,
    onDragOver,
    onDragLeave,
    openPicker,
    pickFiles,
    accept,
  }
}

function NoteCoverUpload() {
  const {
    inputRef,
    file,
    over,
    onDrop,
    onDragOver,
    onDragLeave,
    openPicker,
    pickFiles,
    accept,
  } = useFileDrop('image/*')

  return (
    <>
      <p className="mt-10 text-xs font-medium tracking-wide text-zinc-400 uppercase">
        封面
      </p>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="sr-only"
        onChange={(e) => pickFiles(e.target.files)}
      />
      <button
        type="button"
        onClick={openPicker}
        onDrop={onDrop}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        className={cn(
          'mt-3 flex w-full flex-col items-center justify-center rounded-xl border border-dashed px-4 py-10 transition-colors',
          'border-zinc-300 bg-zinc-50/50 text-zinc-500',
          'hover:border-zinc-500 hover:bg-zinc-50',
          over && 'border-zinc-500 bg-zinc-100/80',
        )}
      >
        <span className="text-sm font-medium text-zinc-600">
          点击或拖拽上传笔记封面
        </span>
        {file ? (
          <span className="mt-2 max-w-full truncate text-xs text-zinc-400">
            已选择：{file.name}
          </span>
        ) : (
          <span className="mt-1.5 text-xs text-zinc-400">
            支持 JPG、PNG、WebP，建议横图 16:9
          </span>
        )}
      </button>
    </>
  )
}

function VideoUploadBlock() {
  const {
    inputRef,
    file,
    over,
    onDrop,
    onDragOver,
    onDragLeave,
    openPicker,
    pickFiles,
    accept,
  } = useFileDrop('video/mp4,video/quicktime,.mp4,.mov')

  return (
    <>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="sr-only"
        onChange={(e) => pickFiles(e.target.files)}
      />
      <button
        type="button"
        onClick={openPicker}
        onDrop={onDrop}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        className={cn(
          'flex h-[240px] w-full flex-col items-center justify-center gap-3 rounded-xl border border-dashed px-6 transition-colors',
          'border-zinc-300 bg-zinc-50/50 text-zinc-500',
          'hover:border-zinc-500 hover:bg-zinc-50',
          over && 'border-zinc-500 bg-zinc-100/80',
        )}
      >
        <Video
          className="size-12 text-zinc-400"
          strokeWidth={1.25}
          aria-hidden
        />
        <span className="max-w-[280px] text-center text-sm font-medium text-zinc-600">
          将视频文件拖拽至此，或点击浏览（支持 MP4 / MOV）
        </span>
        {file ? (
          <span className="max-w-full truncate text-xs text-zinc-400">
            已选择：{file.name}
          </span>
        ) : null}
      </button>
    </>
  )
}

function PublishButton({ onClick }: { onClick: () => void }) {
  return (
    <motion.button
      type="button"
      whileTap={{ scale: 0.94 }}
      transition={{ type: 'spring', stiffness: 520, damping: 28 }}
      className={cn(
        'fixed bottom-8 right-8 z-40',
        'rounded-xl bg-zinc-900 px-6 py-3 text-sm font-semibold text-white shadow-sm',
        'hover:bg-zinc-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900',
      )}
      onClick={onClick}
    >
      发布
    </motion.button>
  )
}

export function ContentPublisher() {
  const [mode, setMode] = useState<PublishMode>('note')

  const handlePublish = useCallback(() => {
    if (mode === 'note') {
      return
    }
  }, [mode])

  return (
    <>
      <div className="mx-auto w-full max-w-[800px] pb-32 pt-1">
        <div className="mb-8 flex gap-10 border-b border-zinc-200/90">
          <button
            type="button"
            onClick={() => setMode('note')}
            className={cn(
              'relative pb-4 text-left text-[20px] leading-tight transition-colors',
              mode === 'note'
                ? 'font-medium text-zinc-900'
                : 'font-normal text-zinc-500 hover:text-zinc-700',
            )}
          >
            <span className="whitespace-nowrap">发布权威笔记</span>
            {mode === 'note' ? (
              <motion.div
                layoutId="publish-mode-underline"
                className="absolute right-0 bottom-0 left-0 h-[3px] rounded-full bg-zinc-900"
                transition={{ type: 'spring', stiffness: 420, damping: 32 }}
              />
            ) : null}
          </button>
          <button
            type="button"
            onClick={() => setMode('video')}
            className={cn(
              'relative pb-4 text-left text-[20px] leading-tight transition-colors',
              mode === 'video'
                ? 'font-medium text-zinc-900'
                : 'font-normal text-zinc-500 hover:text-zinc-700',
            )}
          >
            <span className="whitespace-nowrap">上传科普视频</span>
            {mode === 'video' ? (
              <motion.div
                layoutId="publish-mode-underline"
                className="absolute right-0 bottom-0 left-0 h-[3px] rounded-full bg-zinc-900"
                transition={{ type: 'spring', stiffness: 420, damping: 32 }}
              />
            ) : null}
          </button>
        </div>

        {mode === 'note' ? (
          <motion.div
            key="note"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
            className="rounded-2xl bg-white p-6 shadow-[0_12px_48px_-16px_rgba(15,23,42,0.1)] ring-1 ring-zinc-900/[0.04] md:p-10"
          >
            <label htmlFor="note-title" className="sr-only">
              笔记标题
            </label>
            <input
              id="note-title"
              type="text"
              name="noteTitle"
              autoComplete="off"
              placeholder="输入权威笔记标题..."
              className={cn(
                'w-full border-0 bg-transparent p-0',
                'text-3xl font-bold tracking-tight text-zinc-900',
                'placeholder:text-zinc-300',
                'outline-none focus:outline-none focus-visible:outline-none',
                'focus:ring-0 focus-visible:ring-0',
              )}
            />

            <label htmlFor="note-body" className="sr-only">
              笔记正文
            </label>
            <textarea
              id="note-body"
              name="noteBody"
              rows={14}
              spellCheck={false}
              placeholder="撰写可读、可核查的权威笔记正文..."
              className={cn(
                'mt-8 w-full resize-y border-0 bg-transparent p-0',
                'min-h-[min(52vh,480px)] text-[17px] leading-[1.75] text-zinc-800',
                'placeholder:text-zinc-400',
                'outline-none focus:outline-none focus-visible:outline-none',
                'focus:ring-0 focus-visible:ring-0',
              )}
            />

            <NoteCoverUpload />
          </motion.div>
        ) : (
          <motion.div
            key="video"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
            className="rounded-2xl bg-white p-6 shadow-[0_12px_48px_-16px_rgba(15,23,42,0.1)] ring-1 ring-zinc-900/[0.04] md:p-10"
          >
            <VideoUploadBlock />

            <div className="mt-8 space-y-5">
              <div>
                <label
                  htmlFor="video-title"
                  className="mb-2 block text-xs font-medium tracking-wide text-zinc-500 uppercase"
                >
                  视频标题
                </label>
                <input
                  id="video-title"
                  type="text"
                  name="videoTitle"
                  autoComplete="off"
                  placeholder="填写科普视频标题"
                  className={cn(
                    'w-full rounded-lg border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-900',
                    'placeholder:text-zinc-400',
                    'outline-none transition',
                    'focus:border-zinc-900 focus:bg-white focus:ring-1 focus:ring-zinc-900',
                  )}
                />
              </div>
              <div>
                <label
                  htmlFor="video-desc"
                  className="mb-2 block text-xs font-medium tracking-wide text-zinc-500 uppercase"
                >
                  内容简介
                </label>
                <textarea
                  id="video-desc"
                  name="videoDesc"
                  rows={4}
                  spellCheck={false}
                  placeholder="简要说明视频中的科普要点（选填）"
                  className={cn(
                    'w-full resize-y rounded-lg border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm leading-relaxed text-zinc-900',
                    'placeholder:text-zinc-400',
                    'outline-none transition',
                    'focus:border-zinc-900 focus:bg-white focus:ring-1 focus:ring-zinc-900',
                  )}
                />
              </div>
            </div>
          </motion.div>
        )}
      </div>

      <PublishButton onClick={handlePublish} />
    </>
  )
}
