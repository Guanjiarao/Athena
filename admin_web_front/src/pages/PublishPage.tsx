import { useState } from 'react'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { AdminPublishPage } from '@/pages/AdminPublish'
import { KolPublishHub } from '@/pages/publish/KolPublishHub'

export function PublishPage() {
  const [tab, setTab] = useState<'article' | 'video'>('article')

  return (
    <div className="mx-auto w-full max-w-6xl">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900">
          内容发布
        </h1>
        <p className="mt-1.5 text-sm text-zinc-500">
          发布者可在这里提交图文文章或视频内容，提交后都会进入管理员审核流程。
        </p>
      </div>

      <Tabs
        value={tab}
        onValueChange={(value) => setTab(value as 'article' | 'video')}
        className="gap-0"
      >
        <TabsList
          variant="line"
          className="mb-6 h-10 w-full justify-start gap-6 bg-transparent p-0"
        >
          <TabsTrigger
            value="article"
            className="rounded-none border-0 bg-transparent px-0 text-[15px] font-normal text-zinc-500 data-active:bg-transparent data-active:font-medium data-active:text-zinc-900 data-active:shadow-none data-active:after:bg-zinc-900"
          >
            发布文章
          </TabsTrigger>
          <TabsTrigger
            value="video"
            className="rounded-none border-0 bg-transparent px-0 text-[15px] font-normal text-zinc-500 data-active:bg-transparent data-active:font-medium data-active:text-zinc-900 data-active:shadow-none data-active:after:bg-zinc-900"
          >
            发布视频
          </TabsTrigger>
        </TabsList>

        <TabsContent value="article">
          <AdminPublishPage />
        </TabsContent>
        <TabsContent value="video">
          <KolPublishHub />
        </TabsContent>
      </Tabs>
    </div>
  )
}
