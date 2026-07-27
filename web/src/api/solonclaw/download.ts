import { getFileDownloadUrl } from './files'
import { dashboardFetch } from '../client'

export function getDownloadUrl(filePath: string, fileName?: string): string {
  return getFileDownloadUrl(filePath, fileName)
}

export async function downloadFile(filePath: string, fileName?: string): Promise<void> {
  const res = await dashboardFetch(getDownloadUrl(filePath, fileName))
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(text || `Download failed: ${res.status}`)
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName || filePath.split('/').pop() || 'download.txt'
  document.body.appendChild(a)
  try {
    a.click()
  } finally {
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }
}
