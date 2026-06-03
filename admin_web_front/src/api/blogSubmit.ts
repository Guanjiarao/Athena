import { axiosInstance } from '@/api/axios'

/** 与后端 `/api/blog/submit` 约定的请求体 */
export type OfficialBlogSubmitPayload = {
  title: string
  topicId: number
  topicName: string
  isTop: boolean
  type: number
  coverUrl: string
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
) {
  console.log('【Debug blogSubmit】submitOfficialBlog 被调用，即将发送 POST /api/blog/submit')
  const resp = await axiosInstance.post<SubmitResponseBody>(
    '/api/blog/submit',
    payload,
  )
  console.log('【Debug blogSubmit】请求已返回，status:', resp.status, 'data:', resp.data)
  return resp
}
