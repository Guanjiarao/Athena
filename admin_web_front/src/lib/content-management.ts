import { CATEGORY_MAP } from '@/lib/category-map'

export type AdminContentModuleKey = 'square' | 'recommend' | 'science'

export type ContentQueryConfig =
  | { mode: 'all' }
  | { mode: 'type'; type: number }
  | { mode: 'channel'; channelId: number; type: number }

export type AdminContentOption = {
  id: string
  moduleKey: AdminContentModuleKey
  label: string
  shortLabel: string
  description: string
  contentKind: 'all' | 'note' | 'video' | 'article'
  parentLabel: string
  childLabel?: string
  type?: number
  channelId?: number
  query: ContentQueryConfig
}

export type ScienceContentGroup = {
  id: string
  parent: string
  options: AdminContentOption[]
}

export const ADMIN_CONTENT_MODULES: Array<{
  key: AdminContentModuleKey
  label: string
  description: string
}> = [
  {
    key: 'square',
    label: '广场模块',
    description: '管理广场中的笔记与视频内容。',
  },
  {
    key: 'recommend',
    label: '推荐模块',
    description: '管理推荐位中的视频内容。',
  },
  {
    key: 'science',
    label: '科普模块',
    description: '按阶段与专区管理全部科普文章内容。',
  },
]

export const SQUARE_CONTENT_OPTIONS: AdminContentOption[] = [
  {
    id: 'square-all',
    moduleKey: 'square',
    label: '广场全部内容',
    shortLabel: '全部',
    description: '查看广场下全部内容。',
    contentKind: 'all',
    parentLabel: '广场模块',
    query: { mode: 'all' },
  },
  {
    id: 'square-note',
    moduleKey: 'square',
    label: '广场笔记',
    shortLabel: '笔记',
    description: '查看广场模块中的笔记内容。',
    contentKind: 'note',
    parentLabel: '广场模块',
    childLabel: '笔记',
    type: 1,
    query: { mode: 'type', type: 1 },
  },
  {
    id: 'square-video',
    moduleKey: 'square',
    label: '广场视频',
    shortLabel: '视频',
    description: '查看广场模块中的视频内容。',
    contentKind: 'video',
    parentLabel: '广场模块',
    childLabel: '视频',
    type: 2,
    query: { mode: 'type', type: 2 },
  },
]

export const RECOMMEND_CONTENT_OPTIONS: AdminContentOption[] = [
  {
    id: 'recommend-video',
    moduleKey: 'recommend',
    label: '推荐视频',
    shortLabel: '推荐视频',
    description: '查看推荐模块中的视频内容。',
    contentKind: 'video',
    parentLabel: '推荐模块',
    childLabel: '视频',
    type: 0,
    query: { mode: 'type', type: 0 },
  },
]

export const SCIENCE_CONTENT_GROUPS: ScienceContentGroup[] = CATEGORY_MAP.map(
  (group, groupIndex) => ({
    id: `science-group-${groupIndex}`,
    parent: group.parent,
    options: group.children.map((child) => ({
      id: `science-${child.type}-${child.channelId}`,
      moduleKey: 'science',
      label: `${group.parent} / ${child.name}`,
      shortLabel: child.name,
      description: `查看“${group.parent}”下“${child.name}”的科普文章内容。`,
      contentKind: 'article',
      parentLabel: group.parent,
      childLabel: child.name,
      type: child.type,
      channelId: child.channelId,
      query:
        child.channelId > 0
          ? { mode: 'channel', channelId: child.channelId, type: child.type }
          : { mode: 'type', type: child.type },
    })),
  }),
)

export const SCIENCE_CONTENT_OPTIONS = SCIENCE_CONTENT_GROUPS.flatMap(
  (group) => group.options,
)

export const ALL_ADMIN_CONTENT_OPTIONS: AdminContentOption[] = [
  ...SQUARE_CONTENT_OPTIONS,
  ...RECOMMEND_CONTENT_OPTIONS,
  ...SCIENCE_CONTENT_OPTIONS,
]

const SCIENCE_LOOKUP = new Map<
  string,
  {
    parentLabel: string
    childLabel: string
    type: number
    channelId: number
  }
>()

for (const group of CATEGORY_MAP) {
  for (const child of group.children) {
    SCIENCE_LOOKUP.set(`${child.type}-${child.channelId}`, {
      parentLabel: group.parent,
      childLabel: child.name,
      type: child.type,
      channelId: child.channelId,
    })
  }
}

export function getDefaultOptionId(moduleKey: AdminContentModuleKey): string {
  if (moduleKey === 'square') return SQUARE_CONTENT_OPTIONS[0].id
  if (moduleKey === 'recommend') return RECOMMEND_CONTENT_OPTIONS[0].id
  return SCIENCE_CONTENT_OPTIONS[0]?.id ?? SQUARE_CONTENT_OPTIONS[0].id
}

export function getModuleOptions(
  moduleKey: AdminContentModuleKey,
): AdminContentOption[] {
  if (moduleKey === 'square') return SQUARE_CONTENT_OPTIONS
  if (moduleKey === 'recommend') return RECOMMEND_CONTENT_OPTIONS
  return SCIENCE_CONTENT_OPTIONS
}

export function getContentOptionById(
  optionId: string,
): AdminContentOption | undefined {
  return ALL_ADMIN_CONTENT_OPTIONS.find((item) => item.id === optionId)
}

export function resolveContentKindLabel(type?: number | null): string {
  if (type === 1) return '笔记'
  if (type === 0 || type === 2) return '视频'
  if (typeof type === 'number' && type > 0) return '文章'
  return '未知内容'
}

export function resolveContentModuleLabel(type?: number | null): string {
  if (type === 1 || type === 2) return '广场模块'
  if (type === 0) return '推荐模块'
  if (typeof type === 'number' && type > 0) return '科普模块'
  return '未知模块'
}

export function resolveContentTypeLabel(type?: number | null): string {
  if (type === 1) return '广场笔记'
  if (type === 2) return '广场视频'
  if (type === 0) return '推荐视频'
  if (typeof type === 'number' && type > 0) return '科普文章'
  return typeof type === 'number' ? `未知类型(${type})` : '未知类型'
}

export function resolveScienceCategoryMeta(
  type?: number | null,
  channelId?: number | null,
) {
  if (typeof type !== 'number') return null
  const key = `${type}-${channelId ?? 0}`
  return SCIENCE_LOOKUP.get(key) ?? null
}

export function resolveContentParentLabel(
  type?: number | null,
  channelId?: number | null,
): string {
  if (type === 1 || type === 2) return '广场模块'
  if (type === 0) return '推荐模块'

  const scienceMeta = resolveScienceCategoryMeta(type, channelId)
  if (scienceMeta) return scienceMeta.parentLabel

  if (typeof type === 'number' && type > 0) return '科普模块'
  return '-'
}

export function resolveContentCategoryLabel(
  type?: number | null,
  channelId?: number | null,
  channelName?: string | null,
): string {
  const trimmedChannelName = channelName?.trim()

  if (type === 1) return trimmedChannelName || '广场笔记'
  if (type === 2) return trimmedChannelName || '广场视频'
  if (type === 0) return trimmedChannelName || '推荐视频'

  const scienceMeta = resolveScienceCategoryMeta(type, channelId)
  if (scienceMeta) return trimmedChannelName || scienceMeta.childLabel

  if (typeof type === 'number' && type > 0) return trimmedChannelName || '科普文章'
  return trimmedChannelName || '-'
}

export function resolveStatusMeta(status?: number | null) {
  if (status === 0) {
    return {
      label: '待审核',
      className: 'border-amber-200 bg-amber-50 text-amber-700',
    }
  }
  if (status === 1) {
    return {
      label: '已通过',
      className: 'border-emerald-200 bg-emerald-50 text-emerald-700',
    }
  }
  if (status === 2) {
    return {
      label: '已驳回',
      className: 'border-red-200 bg-red-50 text-red-700',
    }
  }
  return {
    label: '未知状态',
    className: 'border-zinc-200 bg-zinc-50 text-zinc-600',
  }
}
