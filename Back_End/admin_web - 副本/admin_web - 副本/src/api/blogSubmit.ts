import { axiosInstance } from '@/api/axios'

/** 与后端 `/api/blog/submit` 约定的请求体 */
export type OfficialBlogSubmitPayload = {
  userId: number
  title: string
  topicId: number
  topicName: string
  isTop: boolean
  type: number
  coverUrl: string
  imgUrls: string[]
  videoUrl: string
  visible: number
  content: string
  channelId: number
  channelName: string
}

type SubmitResponseBody = {
  code?: number
  message?: string
  data?: unknown
}

export async function submitOfficialBlog(
  payload: OfficialBlogSubmitPayload,
  token?: string,
) {
  return axiosInstance.post<SubmitResponseBody>('/api/blog/submit', payload, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })
}
