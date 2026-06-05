import { axiosInstance } from '@/api/axios'

export type PendingListParams = {
  pageNum: number
  pageSize: number
  type?: number
  channelId?: number
}

export type AuditPendingRow = {
  blogId: number
  title: string
  type: number
  coverUrl?: string | null
  channelName?: string | null
}

export type AuditReviewDetail = {
  noteId: number
  title?: string
  type?: number
  channelId?: number
  channelName?: string
  coverUrl?: string | null
  content?: string
  videoUrl?: string
  imgUrls?: string | string[]
}

type ApiResponse<T> = {
  code?: number
  message?: string
  data?: T
}

export type RejectNotePayload = {
  noteId: number
  reviewRemark: string
}

export async function getPendingList(params: PendingListParams) {
  return axiosInstance.get<ApiResponse<AuditPendingRow[]>>(
    '/api/admin/blog/review/pending',
    { params },
  )
}

export async function getReviewDetail(noteId: number) {
  return axiosInstance.get<ApiResponse<AuditReviewDetail>>('/api/admin/blog/review/detail', {
    params: { noteId },
  })
}

export async function approveNote(noteId: number) {
  return axiosInstance.post<ApiResponse<unknown>>('/api/admin/blog/review/approve', {
    noteId,
  })
}

export async function rejectNote(payload: RejectNotePayload) {
  return axiosInstance.post<ApiResponse<unknown>>('/api/admin/blog/review/reject', payload)
}
