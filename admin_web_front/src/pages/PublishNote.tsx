import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type DragEvent,
  type ReactNode,
} from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import {
  Bold,
  Camera,
  Hash,
  Italic,
  Plus,
  Quote,
  Trash2,
} from 'lucide-react'

import { cn } from '@/lib/utils'

const MAX_IMAGES = 9

type ImageItem = {
  id: string
  url: string
}

function useImageSlots() {
  const [images, setImages] = useState<ImageItem[]>([])
  const imagesRef = useRef<ImageItem[]>([])

  useEffect(() => {
    imagesRef.current = images
  }, [images])

  const addFromFiles = useCallback((fileList: FileList | File[] | null) => {
    if (!fileList || !fileList.length) return
    const files = Array.from(fileList).filter((f) => f.type.startsWith('image/'))
    setImages((prev) => {
      const room = MAX_IMAGES - prev.length
      if (room <= 0) return prev
      const next = files.slice(0, room).map((file) => ({
        id: `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
        url: URL.createObjectURL(file),
      }))
      return [...prev, ...next]
    })
  }, [])

  const remove = useCallback((id: string) => {
    setImages((prev) => {
      const item = prev.find((i) => i.id === id)
      if (item) URL.revokeObjectURL(item.url)
      return prev.filter((i) => i.id !== id)
    })
  }, [])

  useEffect(() => {
    return () => {
      imagesRef.current.forEach((i) => URL.revokeObjectURL(i.url))
    }
  }, [])

  return { images, addFromFiles, remove }
}

function GridImageCell({
  item,
  onRemove,
}: {
  item: ImageItem
  onRemove: (id: string) => void
}) {
  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.92 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.92 }}
      transition={{ type: 'spring', stiffness: 420, damping: 28 }}
      className="relative aspect-square overflow-hidden rounded-lg bg-zinc-100"
    >
      <motion.div
        className="absolute inset-0"
        variants={{ rest: {}, hover: {} }}
        initial="rest"
        whileHover="hover"
      >
        <img
          src={item.url}
          alt=""
          className="absolute inset-0 size-full object-cover"
        />
        <motion.div
          className="pointer-events-none absolute inset-0 rounded-lg bg-black/55"
          variants={{
            rest: { opacity: 0 },
            hover: { opacity: 1 },
          }}
          transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
        />
        <motion.button
          type="button"
          aria-label="删除图片"
          onClick={(e) => {
            e.stopPropagation()
            onRemove(item.id)
          }}
          className="absolute top-2 right-2 z-10 flex size-8 cursor-pointer items-center justify-center rounded-full bg-white/95 text-zinc-800 shadow-sm backdrop-blur-sm transition hover:bg-white"
          variants={{
            rest: { opacity: 0, scale: 0.88 },
            hover: { opacity: 1, scale: 1 },
          }}
          transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
        >
          <Trash2 className="size-4" strokeWidth={2} aria-hidden />
        </motion.button>
      </motion.div>
    </motion.div>
  )
}

function useMarkdownToolbar(body: string, setBody: (s: string) => void) {
  const ref = useRef<HTMLTextAreaElement>(null)

  const apply = useCallback(
    (fn: (sel: string, start: number, end: number, full: string) => { next: string; selStart: number; selEnd: number }) => {
      const el = ref.current
      if (!el) return
      const start = el.selectionStart
      const end = el.selectionEnd
      const selected = body.slice(start, end)
      const { next, selStart, selEnd } = fn(selected, start, end, body)
      setBody(next)
      requestAnimationFrame(() => {
        el.focus()
        el.setSelectionRange(selStart, selEnd)
      })
    },
    [body, setBody],
  )

  const bold = useCallback(() => {
    apply((sel, s, e, full) => {
      const ins = `**${sel || '加粗文字'}**`
      const next = full.slice(0, s) + ins + full.slice(e)
      const pad = sel ? 2 : 2
      return {
        next,
        selStart: s + pad,
        selEnd: s + pad + (sel || '加粗文字').length,
      }
    })
  }, [apply])

  const italic = useCallback(() => {
    apply((sel, s, e, full) => {
      const ins = `*${sel || '斜体文字'}*`
      const next = full.slice(0, s) + ins + full.slice(e)
      return {
        next,
        selStart: s + 1,
        selEnd: s + 1 + (sel || '斜体文字').length,
      }
    })
  }, [apply])

  const quote = useCallback(() => {
    apply((sel, s, e, full) => {
      const block = sel || '引用内容'
      const lines = block.split('\n').map((l) => `> ${l}`)
      const ins = lines.join('\n')
      const next = full.slice(0, s) + ins + full.slice(e)
      return { next, selStart: s, selEnd: s + ins.length }
    })
  }, [apply])

  const tag = useCallback(() => {
    apply((sel, s, e, full) => {
      const ins = sel ? sel : '#健康科普 '
      const next = full.slice(0, s) + ins + full.slice(e)
      const endPos = s + ins.length
      return {
        next,
        selStart: endPos,
        selEnd: endPos,
      }
    })
  }, [apply])

  return { ref, bold, italic, quote, tag }
}

function ToolbarIconButton({
  label,
  onClick,
  children,
}: {
  label: string
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      className={cn(
        'inline-flex size-9 items-center justify-center rounded-md text-zinc-800',
        'transition hover:bg-white hover:text-zinc-900 hover:shadow-sm',
      )}
    >
      {children}
    </button>
  )
}

/** KOL 权威笔记表单（九宫格 + Markdown），不含外层 Tab 与固定发布按钮 */
export function PublishNoteForm() {
  const inputId = useId()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [dropOver, setDropOver] = useState(false)
  const { images, addFromFiles, remove } = useImageSlots()
  const { ref: textareaRef, bold, italic, quote, tag } = useMarkdownToolbar(
    body,
    setBody,
  )

  const count = images.length
  const canAddMore = count < MAX_IMAGES

  const openPicker = useCallback(() => fileInputRef.current?.click(), [])

  const onFileChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      addFromFiles(e.target.files)
      e.target.value = ''
    },
    [addFromFiles],
  )

  const onDrop = useCallback(
    (e: DragEvent) => {
      e.preventDefault()
      setDropOver(false)
      addFromFiles(e.dataTransfer.files)
    },
    [addFromFiles],
  )

  return (
    <div className="rounded-2xl border border-zinc-200/80 bg-white p-6 shadow-xl md:p-10">
      <label htmlFor={`${inputId}-title`} className="sr-only">
        笔记标题
      </label>
      <input
        id={`${inputId}-title`}
        type="text"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
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

      <div className="mt-8">
        <div className="mb-3 flex items-center justify-between">
          <span className="text-xs font-medium tracking-wide text-zinc-400 uppercase">
            配图
          </span>
          <span className="tabular-nums text-xs font-medium text-zinc-500">
            {count}/{MAX_IMAGES} 图片
          </span>
        </div>

        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          multiple
          className="sr-only"
          aria-label="上传图片"
          onChange={onFileChange}
        />

        {count === 0 ? (
          <button
            type="button"
            onClick={openPicker}
            onDrop={onDrop}
            onDragOver={(e) => {
              e.preventDefault()
              setDropOver(true)
            }}
            onDragLeave={() => setDropOver(false)}
            className={cn(
              'flex aspect-square w-full max-w-full flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-zinc-300 transition-colors',
              'bg-zinc-50/50 text-zinc-500',
              'hover:border-zinc-500',
              dropOver && 'border-zinc-500 bg-zinc-100/80',
            )}
          >
            <Camera className="size-10 text-zinc-400" strokeWidth={1.25} aria-hidden />
            <span className="text-sm font-medium text-zinc-600">
              添加图片 (最多9张)
            </span>
          </button>
        ) : (
          <div
            className={cn(
              'grid grid-cols-3 gap-2',
              dropOver && 'rounded-xl ring-2 ring-zinc-400 ring-offset-2 ring-offset-zinc-50',
            )}
            onDragOver={(e) => {
              e.preventDefault()
              if (canAddMore) setDropOver(true)
            }}
            onDragLeave={() => setDropOver(false)}
            onDrop={onDrop}
          >
            <AnimatePresence mode="popLayout">
              {images.map((item) => (
                <GridImageCell key={item.id} item={item} onRemove={remove} />
              ))}
            </AnimatePresence>
            {canAddMore ? (
              <motion.button
                type="button"
                layout
                initial={{ opacity: 0, scale: 0.92 }}
                animate={{ opacity: 1, scale: 1 }}
                onClick={openPicker}
                className={cn(
                  'flex aspect-square items-center justify-center rounded-lg border border-dashed border-zinc-300',
                  'bg-zinc-50/50 text-zinc-400 transition-colors',
                  'hover:border-zinc-500 hover:bg-zinc-100 hover:text-zinc-500',
                )}
                aria-label="继续添加图片"
              >
                <Plus className="size-9 stroke-[1.5]" aria-hidden />
              </motion.button>
            ) : null}
          </div>
        )}
      </div>

      <div className="mt-10">
        <div className="flex flex-wrap gap-1 rounded-t-lg bg-zinc-100 px-2 py-2 ring-1 ring-zinc-200/80">
          <ToolbarIconButton label="加粗" onClick={bold}>
            <Bold className="size-4" aria-hidden />
          </ToolbarIconButton>
          <ToolbarIconButton label="斜体" onClick={italic}>
            <Italic className="size-4" aria-hidden />
          </ToolbarIconButton>
          <ToolbarIconButton label="引用" onClick={quote}>
            <Quote className="size-4" aria-hidden />
          </ToolbarIconButton>
          <ToolbarIconButton label="健康标签" onClick={tag}>
            <Hash className="size-4" aria-hidden />
          </ToolbarIconButton>
        </div>
        <textarea
          ref={textareaRef}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          spellCheck={false}
          rows={14}
          placeholder="在此输入专业、严谨的医疗健康知识...（支持 Markdown）"
          className={cn(
            'w-full resize-y rounded-b-lg border border-zinc-200 bg-white px-4 py-4 text-[15px] leading-[1.75]',
            'text-[#666666] placeholder:text-zinc-400',
            'outline-none transition',
            'focus-visible:border-zinc-900 focus-visible:ring-1 focus-visible:ring-zinc-900',
          )}
        />
      </div>
    </div>
  )
}
