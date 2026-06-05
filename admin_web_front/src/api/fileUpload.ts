import { isAxiosError } from 'axios'

import { axiosInstance } from '@/api/axios'

function normalizeMediaUrl(raw: string): string {
  const s = raw.trim()
  if (/^https?:\/\//i.test(s)) return s
  if (s.startsWith('//')) return `${window.location.protocol}${s}`
  if (s.startsWith('/')) return `${window.location.origin}${s}`
  return s
}

function extractFileUrlFromResponse(data: unknown): string | null {
  if (typeof data === 'string' && data.trim()) {
    return normalizeMediaUrl(data)
  }
  if (!data || typeof data !== 'object') return null
  const o = data as Record<string, unknown>

  if (typeof o.url === 'string' && o.url) return normalizeMediaUrl(o.url)

  if (o.code === 200 && typeof o.data === 'string' && o.data) {
    return normalizeMediaUrl(o.data)
  }

  if (o.data && typeof o.data === 'object') {
    const d = o.data as Record<string, unknown>
    if (typeof d.url === 'string' && d.url) return normalizeMediaUrl(d.url)
    if (typeof d.fileUrl === 'string' && d.fileUrl) {
      return normalizeMediaUrl(d.fileUrl)
    }
    if (typeof d.path === 'string' && d.path) return normalizeMediaUrl(d.path)
  }

  return null
}

/**
 * 上传文件至 `/api/file/upload`，返回可访问的 URL。
 * Authorization 由全局拦截器自动注入。
 */
export async function uploadMediaFile(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)

  try {
    const { data } = await axiosInstance.post<unknown>('/api/file/upload', form)
    const url = extractFileUrlFromResponse(data)
    if (!url) {
      throw new Error('上传成功但无法解析文件地址')
    }
    return url
  } catch (e) {
    if (isAxiosError(e)) {
      const body = e.response?.data as { message?: string } | undefined
      if (body && typeof body.message === 'string' && body.message) {
        throw new Error(body.message)
      }
    }
    if (e instanceof Error) throw e
    throw new Error('上传失败')
  }
}
