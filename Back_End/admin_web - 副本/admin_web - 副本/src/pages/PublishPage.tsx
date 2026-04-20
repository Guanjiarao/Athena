import { useAuth } from '@/contexts/AuthContext'
import { ContentPublisher } from '@/pages/publish/ContentPublisher'
import { KolPublishHub } from '@/pages/publish/KolPublishHub'

export function PublishPage() {
  const { user } = useAuth()
  if (user?.role === 'kol') {
    return <KolPublishHub />
  }
  return <ContentPublisher />
}
