import { useCallback, useEffect, useMemo, useRef } from 'react'
import ReactQuill from 'react-quill-new'

import { uploadMediaFile } from '@/api/fileUpload'
import { replacePendingEmbeddedImagesInHtml } from '@/lib/article-content'

import 'react-quill-new/dist/quill.snow.css'
import './article-rich-editor.css'

type ArticleRichEditorProps = {
  value: string
  onChange: (html: string) => void
  placeholder?: string
  onImageUploadError?: (message: string) => void
}

const QUILL_FORMATS = ['header', 'bold', 'list', 'image'] as const

function getQuillEditor(instance: ReactQuill | null) {
  if (!instance) return null

  try {
    return instance.getEditor()
  } catch {
    return null
  }
}

function getInsertIndex(
  quill: ReturnType<typeof getQuillEditor>,
  startIndex?: number,
) {
  if (!quill) return 0

  return (
    startIndex ??
    quill.getSelection(true)?.index ??
    Math.max(0, quill.getLength() - 1)
  )
}

function getClipboardImageFiles(clipboardData: DataTransfer) {
  const filesFromItems = Array.from(clipboardData.items)
    .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
    .map((item) => item.getAsFile())
    .filter((file): file is File => file instanceof File)

  if (filesFromItems.length > 0) return filesFromItems

  return Array.from(clipboardData.files).filter((file) =>
    file.type.startsWith('image/'),
  )
}

export function ArticleRichEditor({
  value,
  onChange,
  placeholder,
  onImageUploadError,
}: ArticleRichEditorProps) {
  const quillRef = useRef<ReactQuill>(null)

  const insertImages = useCallback(
    async (files: File[], startIndex?: number) => {
      const quill = getQuillEditor(quillRef.current)
      if (!quill) return

      let index = getInsertIndex(quill, startIndex)

      for (const file of files) {
        if (!file.type.startsWith('image/')) continue

        try {
          const url = await uploadMediaFile(file)
          const activeQuill = getQuillEditor(quillRef.current)
          if (!activeQuill) return

          const insertAt = Math.min(
            index,
            Math.max(0, activeQuill.getLength() - 1),
          )

          activeQuill.insertEmbed(insertAt, 'image', url, 'user')
          index = insertAt + 1
          activeQuill.setSelection(index, 0, 'silent')
        } catch (err) {
          const msg =
            err instanceof Error ? err.message : '图片上传失败，请重试'
          onImageUploadError?.(msg)
        }
      }
    },
    [onImageUploadError],
  )

  const insertHtmlWithUploadedImages = useCallback(
    async (html: string, startIndex?: number) => {
      const quill = getQuillEditor(quillRef.current)
      if (!quill) return

      const insertAt = getInsertIndex(quill, startIndex)

      try {
        const { html: processedHtml, failed } =
          await replacePendingEmbeddedImagesInHtml(html)
        const activeQuill = getQuillEditor(quillRef.current)
        if (!activeQuill) return

        activeQuill.clipboard.dangerouslyPasteHTML(
          insertAt,
          processedHtml,
          'user',
        )
        activeQuill.setSelection(
          Math.min(activeQuill.getLength() - 1, insertAt + 1),
          0,
          'silent',
        )

        if (failed > 0) {
          onImageUploadError?.('部分粘贴图片上传失败，请重新粘贴或改用上传按钮。')
        }
      } catch (err) {
        const msg =
          err instanceof Error ? err.message : '图片粘贴上传失败，请重试'
        onImageUploadError?.(msg)
      }
    },
    [onImageUploadError],
  )

  const imageHandler = useCallback(() => {
    const input = document.createElement('input')
    input.setAttribute('type', 'file')
    input.setAttribute('accept', 'image/*')
    input.click()

    input.onchange = () => {
      const file = input.files?.[0]
      if (!file) return

      void insertImages([file])
    }
  }, [insertImages])

  const handlePaste = useCallback(
    (event: ClipboardEvent) => {
      const clipboardData = event.clipboardData
      if (!clipboardData) return

      const clipboardHtml = clipboardData.getData('text/html').trim()
      if (clipboardHtml && /<img[\s>]/i.test(clipboardHtml)) {
        event.preventDefault()
        event.stopPropagation()
        event.stopImmediatePropagation?.()

        const quill = getQuillEditor(quillRef.current)
        const startIndex = getInsertIndex(quill)
        void insertHtmlWithUploadedImages(clipboardHtml, startIndex)
        return
      }

      const imageFiles = getClipboardImageFiles(clipboardData)
      if (imageFiles.length === 0) return

      event.preventDefault()
      event.stopPropagation()
      event.stopImmediatePropagation?.()

      const quill = getQuillEditor(quillRef.current)
      const startIndex = getInsertIndex(quill)

      void insertImages(imageFiles, startIndex)
    },
    [insertHtmlWithUploadedImages, insertImages],
  )

  useEffect(() => {
    const quill = getQuillEditor(quillRef.current)
    if (!quill) return

    const root = quill.root as HTMLElement
    const onNativePaste = (event: ClipboardEvent) => {
      if (!root.contains(event.target as Node | null)) return
      handlePaste(event)
    }

    root.addEventListener('paste', onNativePaste, true)
    return () => {
      root.removeEventListener('paste', onNativePaste, true)
    }
  }, [handlePaste])

  const modules = useMemo(
    () => ({
      toolbar: {
        container: [
          [{ header: [1, 2, 3, false] }],
          ['bold'],
          [{ list: 'ordered' }, { list: 'bullet' }],
          ['image'],
        ],
        handlers: {
          image: imageHandler,
        },
      },
    }),
    [imageHandler],
  )

  return (
    <div className="article-rich-editor">
      <ReactQuill
        ref={quillRef}
        theme="snow"
        value={value}
        onChange={onChange}
        modules={modules}
        formats={[...QUILL_FORMATS]}
        placeholder={placeholder}
      />
    </div>
  )
}
