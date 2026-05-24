import { axiosInstance } from '@/api/axios'

export type ContentListParams = {
  pageNum: number
  pageSize: number
}

export type ContentListByTypeParams = ContentListParams & {
  type: number
}

export type ContentListByChannelParams = ContentListParams & {
  channelId: number
}

export type ContentListRow = {
  blogId: number
  type?: number | null
  coverUrl?: string | null
  title?: string | null
  likeTotal?: number | null
  status?: number | null
  reviewRemark?: string | null
  channelId?: number | null
  channelName?: string | null
  userDTO?: {
    userId?: number | null
    nickName?: string | null
    icon?: string | null
    priority?: boolean | null
  } | null
}

export type PublishedContentDetail = {
  blogId?: number | null
  noteId?: number | null
  title?: string | null
  type?: number | null
  channelId?: number | null
  channelName?: string | null
  coverUrl?: string | null
  content?: string | null
  videoUrl?: string | null
  imgUrls?: string | string[] | null
  likeTotal?: number | null
  status?: number | null
  reviewRemark?: string | null
  visible?: number | null
  createTime?: string | null
  updateTime?: string | null
  userDTO?: {
    userId?: number | null
    nickName?: string | null
    icon?: string | null
    priority?: boolean | null
  } | null
}

type ApiResponse<T> = {
  code?: number
  message?: string
  data?: T
  total?: number | null
}

export async function getSquareContentList(params: ContentListParams) {
  return axiosInstance.get<ApiResponse<ContentListRow[]>>('/api/blog/list', {
    params,
  })
}

export async function getContentListByType(
  params: ContentListByTypeParams,
) {
  return axiosInstance.get<ApiResponse<ContentListRow[]>>(
    '/api/blog/listByTypeId',
    {
      params,
    },
  )
}

export async function getContentListByChannel(
  params: ContentListByChannelParams,
) {
  return axiosInstance.get<ApiResponse<ContentListRow[]>>(
    '/api/blog/listBychannelId',
    {
      params,
    },
  )
}

export async function getPublishedContentDetail(blogId: number, type: number) {
  return axiosInstance.get<ApiResponse<PublishedContentDetail>>(
    '/api/blog/Detail',
    {
      params: {
        blog_id: blogId,
        type,
      },
    },
  )
}

export async function deletePublishedContent(
  blogId: number,
  reviewRemark: string,
) {
  return axiosInstance.delete<ApiResponse<unknown>>(`/api/blog/${blogId}`, {
    data: {
      reviewRemark,
    },
  })
}
