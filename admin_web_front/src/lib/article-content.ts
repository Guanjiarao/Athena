import { uploadMediaFile } from '@/api/fileUpload'

const EMBEDDED_IMAGE_FILE_PREFIX = 'article-embedded-image'

function shouldUploadEmbeddedImageSrc(src: string) {
  const normalized = src.trim().toLowerCase()
  return normalized.startsWith('data:image/') || normalized.startsWith('blob:')
}

async function createImageFileFromSource(src: string, index: number) {
  try {
    const response = await fetch(src)
    if (!response.ok) return null

    const blob = await response.blob()
    if (!blob.type.startsWith('image/')) return null

    const extension = blob.type.split('/')[1]?.split('+')[0] ?? 'png'
    return new File(
      [blob],
      `${EMBEDDED_IMAGE_FILE_PREFIX}-${Date.now()}-${index}.${extension}`,
      { type: blob.type },
    )
  } catch {
    return null
  }
}

export async function replacePendingEmbeddedImagesInHtml(html: string) {
  if (!html.trim()) {
    return {
      html,
      replaced: 0,
      failed: 0,
    }
  }

  const doc = new DOMParser().parseFromString(html, 'text/html')
  const images = Array.from(doc.querySelectorAll('img'))
  let replaced = 0
  let failed = 0

  for (const [index, image] of images.entries()) {
    const src = image.getAttribute('src')?.trim()
    if (!src || !shouldUploadEmbeddedImageSrc(src)) continue

    const file = await createImageFileFromSource(src, index)
    if (!file) {
      failed += 1
      continue
    }

    try {
      const url = await uploadMediaFile(file)
      image.setAttribute('src', url)
      image.removeAttribute('srcset')
      replaced += 1
    } catch {
      failed += 1
    }
  }

  return {
    html: doc.body.innerHTML,
    replaced,
    failed,
  }
}
