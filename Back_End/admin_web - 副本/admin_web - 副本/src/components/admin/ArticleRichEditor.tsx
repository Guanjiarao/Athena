import { useCallback, useMemo, useRef } from 'react'
import ReactQuill from "react-quill-new";

import { uploadMediaFile } from '@/api/fileUpload'

import "react-quill-new/dist/quill.snow.css";
import './article-rich-editor.css'

type ArticleRichEditorProps = {
  value: string
  onChange: (html: string) => void
  placeholder?: string
  token?: string
  onImageUploadError?: (message: string) => void
}

const QUILL_FORMATS = ['header', 'bold', 'list', 'image'] as const

export function ArticleRichEditor({
  value,
  onChange,
  placeholder,
  token,
  onImageUploadError,
}: ArticleRichEditorProps) {
  const quillRef = useRef<ReactQuill>(null)

  const imageHandler = useCallback(() => {
    const input = document.createElement('input')
    input.setAttribute('type', 'file')
    input.setAttribute('accept', 'image/*')
    input.click()

    input.onchange = async () => {
      const file = input.files?.[0]
      if (!file) return

      try {
        const url = await uploadMediaFile(file, token)
        const quill = quillRef.current?.getEditor()
        if (!quill) return

        const range = quill.getSelection(true)
        const index = range?.index ?? Math.max(0, quill.getLength() - 1)

        // Quill 图片嵌入；导出 HTML 即为带 src 的 <img>
        quill.insertEmbed(index, 'image', url, 'user')
        quill.setSelection(index + 1, 0, 'silent')
      } catch (err) {
        const msg =
          err instanceof Error ? err.message : '图片上传失败，请重试'
        onImageUploadError?.(msg)
      }
    }
  }, [token, onImageUploadError])

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
