export type ArticleCategoryChild = {
  name: string
  type: number
  channelId: number
}

export type ArticleCategoryGroup = {
  parent: string
  children: ArticleCategoryChild[]
}

/**
 * 分类字典：与后端 type / channelId 对齐
 */
export const CATEGORY_MAP: ArticleCategoryGroup[] = [
  {
    parent: '0~12岁阶段',
    children: [
      { name: '我是小朋友', type: 10, channelId: 0 },
      { name: '我是家长', type: 11, channelId: 0 },
    ],
  },
  {
    parent: '12~22岁阶段',
    children: [
      { name: '科学生理期', type: 30, channelId: 0 },
      { name: '认识两性', type: 31, channelId: 0 },
    ],
  },
  {
    parent: '22~55岁阶段',
    children: [
      { name: '护肤指南', type: 50, channelId: 0 },
      { name: '科学备孕', type: 51, channelId: 0 },
      { name: '避孕指南', type: 52, channelId: 0 },
      { name: '孕期护理', type: 53, channelId: 0 },
      { name: '月子期恢复', type: 54, channelId: 0 },
      { name: '生育科普', type: 55, channelId: 0 },
    ],
  },
  {
    parent: '55岁以上阶段',
    children: [
      { name: '疾病预防', type: 70, channelId: 0 },
      { name: '疾病先兆', type: 71, channelId: 0 },
      { name: '科学养生', type: 72, channelId: 0 },
      { name: '正视更年期', type: 73, channelId: 0 },
    ],
  },
  {
    parent: '四大核心专区',
    children: [
      { name: '个性化护肤', type: 127, channelId: 1 },
      { name: '健身', type: 127, channelId: 2 },
      { name: '全龄心理健康', type: 127, channelId: 3 },
      { name: '避孕指南', type: 127, channelId: 4 },
    ],
  },
]
