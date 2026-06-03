import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ImageIcon, MoreHorizontal, Video } from 'lucide-react'

import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'

type AuditStatus = 'pending' | 'review' | 'approved'

type NoteRow = {
  id: string
  previewUrl: string
  title: string
  creator: string
  submittedAt: string
  status: AuditStatus
}

type VideoRow = NoteRow & { duration: string }

const statusMap: Record<
  AuditStatus,
  { label: string; dot: string; ring: string }
> = {
  pending: {
    label: '待审',
    dot: 'bg-zinc-400',
    ring: 'ring-zinc-400/35',
  },
  review: {
    label: '复审中',
    dot: 'bg-zinc-600',
    ring: 'ring-zinc-600/35',
  },
  approved: {
    label: '已通过',
    dot: 'bg-zinc-900',
    ring: 'ring-zinc-900/30',
  },
}

const mockNotes: NoteRow[] = [
  {
    id: 'n1',
    previewUrl: 'https://picsum.photos/seed/athena-note1/120/120',
    title: '周末探店｜老城区这家手冲馆，豆子清单值得收藏',
    creator: '咖啡旅人_阿哲',
    submittedAt: '2026-04-11 09:24',
    status: 'pending',
  },
  {
    id: 'n2',
    previewUrl: 'https://picsum.photos/seed/athena-note2/120/120',
    title: '三分钟读懂：新手如何配置第一张信用卡',
    creator: '理性消费志',
    submittedAt: '2026-04-11 08:56',
    status: 'review',
  },
  {
    id: 'n3',
    previewUrl: 'https://picsum.photos/seed/athena-note3/120/120',
    title: '春日穿搭｜小个子友好的一周通勤灵感',
    creator: 'Mio_穿搭笔记',
    submittedAt: '2026-04-10 22:18',
    status: 'approved',
  },
]

const mockVideos: VideoRow[] = [
  {
    id: 'v1',
    previewUrl: 'https://picsum.photos/seed/athena-v1/120/120',
    title: '15 秒带你看完新品开箱：降噪耳机实录音质对比',
    creator: '数码简报',
    submittedAt: '2026-04-11 10:02',
    status: 'pending',
    duration: '00:18',
  },
  {
    id: 'v2',
    previewUrl: 'https://picsum.photos/seed/athena-v2/120/120',
    title: '厨房小白也能做｜溏心蛋温泉蛋零失败教程',
    creator: '好好吃饭 TV',
    submittedAt: '2026-04-11 07:41',
    status: 'pending',
    duration: '01:05',
  },
  {
    id: 'v3',
    previewUrl: 'https://picsum.photos/seed/athena-v3/120/120',
    title: 'Citywalk 上海｜黄昏外滩电影感运镜分享',
    creator: 'FilmWalk_鹿',
    submittedAt: '2026-04-10 19:33',
    status: 'review',
    duration: '00:42',
  },
]

function StatusTag({ status }: { status: AuditStatus }) {
  const cfg = statusMap[status]
  return (
    <span className="inline-flex items-center gap-2 text-sm text-zinc-800">
      <span
        className={cn(
          'inline-flex size-2 shrink-0 rounded-full ring-2 ring-offset-1 ring-offset-white',
          cfg.dot,
          cfg.ring,
        )}
        aria-hidden
      />
      {cfg.label}
    </span>
  )
}

function RowActions() {
  return (
    <div className="relative flex h-11 items-center justify-end pr-1">
      <button
        type="button"
        className={cn(
          'inline-flex size-9 items-center justify-center rounded-lg text-zinc-400 transition-all duration-200',
          'hover:bg-zinc-100 hover:text-zinc-600',
          'opacity-100 group-hover:pointer-events-none group-hover:opacity-0 group-hover:scale-95',
        )}
        aria-label="更多操作"
      >
        <MoreHorizontal className="size-5 text-zinc-400" strokeWidth={1.75} />
      </button>
      <div
        className={cn(
          'pointer-events-none absolute inset-y-0 right-0 flex items-center gap-2 pr-0',
          'translate-x-1 opacity-0 transition-all duration-200 ease-out',
          'group-hover:pointer-events-auto group-hover:translate-x-0 group-hover:opacity-100',
        )}
      >
        <button
          type="button"
          className={cn(
            'rounded-lg border border-zinc-300 bg-white px-3 py-1.5 text-sm font-medium text-zinc-800',
            'shadow-sm transition hover:border-zinc-400 hover:bg-zinc-50',
          )}
        >
          驳回
        </button>
        <button
          type="button"
          className={cn(
            'rounded-lg border border-zinc-900 bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white',
            'shadow-sm transition hover:border-zinc-800 hover:bg-zinc-800',
          )}
        >
          通过
        </button>
      </div>
    </div>
  )
}

function NotesTable({ rows }: { rows: NoteRow[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[880px] border-collapse">
        <thead>
          <tr>
            <th className="w-[100px] pb-3 pl-0 pr-4 pt-1 text-left text-xs font-medium tracking-wide text-zinc-500">
              预览
            </th>
            <th className="pb-3 pr-4 pt-1 text-left text-xs font-medium tracking-wide text-zinc-500">
              标题
            </th>
            <th className="w-[140px] pb-3 pr-4 pt-1 text-left text-xs font-medium tracking-wide text-zinc-500">
              创作者
            </th>
            <th className="w-[168px] pb-3 pr-4 pt-1 text-left text-xs font-medium tracking-wide text-zinc-500">
              提交时间
            </th>
            <th className="w-[120px] pb-3 pr-4 pt-1 text-left text-xs font-medium tracking-wide text-zinc-500">
              状态
            </th>
            <th className="w-[200px] pb-3 pl-4 pr-0 pt-1 text-right text-xs font-medium tracking-wide text-zinc-500">
              操作
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-zinc-100">
          {rows.map((row) => (
            <tr
              key={row.id}
              className={cn(
                'group transition-colors duration-150 hover:bg-zinc-50/90',
              )}
            >
              <td className="py-5 pl-0 pr-4 align-middle">
                <img
                  src={row.previewUrl}
                  alt=""
                  className="size-14 rounded-lg object-cover shadow-sm ring-1 ring-black/[0.04]"
                  loading="lazy"
                />
              </td>
              <td className="max-w-[320px] py-5 pr-4 align-middle">
                <span className="line-clamp-2 text-[15px] font-medium leading-snug text-zinc-900">
                  {row.title}
                </span>
              </td>
              <td className="py-5 pr-4 align-middle text-sm text-zinc-600">
                {row.creator}
              </td>
              <td className="py-5 pr-4 align-middle text-sm tabular-nums text-zinc-500">
                {row.submittedAt}
              </td>
              <td className="py-5 pr-4 align-middle">
                <StatusTag status={row.status} />
              </td>
              <td className="py-5 pl-4 pr-0 align-middle">
                <RowActions />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function VideosTable({ rows }: { rows: VideoRow[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[920px] border-collapse">
        <thead>
          <tr>
            <th className="w-[100px] pb-3 pl-0 pr-4 pt-1 text-left text-xs font-medium tracking-wide text-zinc-500">
              预览
            </th>
            <th className="pb-3 pr-4 pt-1 text-left text-xs font-medium tracking-wide text-zinc-500">
              标题
            </th>
            <th className="w-[140px] pb-3 pr-4 pt-1 text-left text-xs font-medium tracking-wide text-zinc-500">
              创作者
            </th>
            <th className="w-[168px] pb-3 pr-4 pt-1 text-left text-xs font-medium tracking-wide text-zinc-500">
              提交时间
            </th>
            <th className="w-[120px] pb-3 pr-4 pt-1 text-left text-xs font-medium tracking-wide text-zinc-500">
              状态
            </th>
            <th className="w-[200px] pb-3 pl-4 pr-0 pt-1 text-right text-xs font-medium tracking-wide text-zinc-500">
              操作
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-zinc-100">
          {rows.map((row) => (
            <tr
              key={row.id}
              className={cn(
                'group transition-colors duration-150 hover:bg-zinc-50/90',
              )}
            >
              <td className="py-5 pl-0 pr-4 align-middle">
                <div className="relative size-14 shrink-0 overflow-hidden rounded-lg shadow-sm ring-1 ring-black/[0.04]">
                  <img
                    src={row.previewUrl}
                    alt=""
                    className="size-full object-cover"
                    loading="lazy"
                  />
                  <span className="absolute bottom-1 right-1 rounded bg-black/55 px-1 py-0.5 text-[10px] font-medium tabular-nums text-white backdrop-blur-[2px]">
                    {row.duration}
                  </span>
                  <span className="absolute inset-0 flex items-center justify-center bg-black/20 opacity-0 transition-opacity group-hover:opacity-100">
                    <Video className="size-6 text-white drop-shadow" aria-hidden />
                  </span>
                </div>
              </td>
              <td className="max-w-[320px] py-5 pr-4 align-middle">
                <span className="line-clamp-2 text-[15px] font-medium leading-snug text-zinc-900">
                  {row.title}
                </span>
              </td>
              <td className="py-5 pr-4 align-middle text-sm text-zinc-600">
                {row.creator}
              </td>
              <td className="py-5 pr-4 align-middle text-sm tabular-nums text-zinc-500">
                {row.submittedAt}
              </td>
              <td className="py-5 pr-4 align-middle">
                <StatusTag status={row.status} />
              </td>
              <td className="py-5 pl-4 pr-0 align-middle">
                <RowActions />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

const tabPanelMotion = {
  initial: { opacity: 0, y: 6 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -4 },
  transition: { duration: 0.22, ease: [0.22, 1, 0.36, 1] as const },
}

export function AuditDashboard() {
  const [tab, setTab] = useState<'notes' | 'video'>('notes')

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900">
          内容审核台
        </h1>
        <p className="mt-1.5 text-sm text-zinc-500">
          按内容形态分流审核队列，支持快速通过或驳回。
        </p>
      </div>

      <Tabs
        value={tab}
        onValueChange={(v) => setTab(v as 'notes' | 'video')}
        className="gap-0"
      >
        <TabsList variant="line" className="mb-6 h-10 w-full justify-start gap-6 bg-transparent p-0">
          <TabsTrigger
            value="notes"
            className="rounded-none border-0 bg-transparent px-0 text-[15px] font-normal text-zinc-500 data-active:bg-transparent data-active:font-medium data-active:text-zinc-900 data-active:shadow-none data-active:after:bg-zinc-900"
          >
            <ImageIcon className="size-4 text-zinc-800" aria-hidden />
            图文笔记
          </TabsTrigger>
          <TabsTrigger
            value="video"
            className="rounded-none border-0 bg-transparent px-0 text-[15px] font-normal text-zinc-500 data-active:bg-transparent data-active:font-medium data-active:text-zinc-900 data-active:shadow-none data-active:after:bg-zinc-900"
          >
            <Video className="size-4 text-zinc-800" aria-hidden />
            短视频
          </TabsTrigger>
        </TabsList>
      </Tabs>

      <div className="rounded-2xl border border-zinc-200/80 bg-white px-2 py-1 shadow-sm">
        <AnimatePresence mode="wait">
          {tab === 'notes' ? (
            <motion.div
              key="notes"
              {...tabPanelMotion}
              className="px-4 py-5 md:px-6"
            >
              <NotesTable rows={mockNotes} />
            </motion.div>
          ) : (
            <motion.div
              key="video"
              {...tabPanelMotion}
              className="px-4 py-5 md:px-6"
            >
              <VideosTable rows={mockVideos} />
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}
