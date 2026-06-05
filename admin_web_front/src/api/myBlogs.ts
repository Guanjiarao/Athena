import { axiosInstance } from '@/api/axios'

export type MyBlogListParams = {
  pageNum?: number
  pageSize?: number
}

export type MyBlogRow = {
  blogId: number
  type?: number | null
  coverUrl?: string | null
  title?: string | null
  likeTotal?: number | null
  status?: number | null
  reviewRemark?: string | null
  channelId?: number | null
  channelName?: string | null
}

type ApiResponse<T> = {
  code?: number
  message?: string
  data?: T
}

export async function getMyBlogList(params: MyBlogListParams = {}) {
  return axiosInstance.get<ApiResponse<MyBlogRow[]>>('/api/blog/myList', {
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 50,
    },
  })
}
