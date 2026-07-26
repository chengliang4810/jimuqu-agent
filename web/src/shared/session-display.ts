const SOURCE_LABEL_KEYS: Record<string, string> = {
  telegram: 'sessionSources.telegram',
  api_server: 'sessionSources.apiServer',
  cli: 'sessionSources.cli',
  discord: 'sessionSources.discord',
  slack: 'sessionSources.slack',
  matrix: 'sessionSources.matrix',
  whatsapp: 'sessionSources.whatsapp',
  signal: 'sessionSources.signal',
  email: 'sessionSources.email',
  sms: 'sessionSources.sms',
  dingtalk: 'sessionSources.dingtalk',
  feishu: 'sessionSources.feishu',
  wecom: 'sessionSources.wecom',
  weixin: 'sessionSources.weixin',
  bluebubbles: 'sessionSources.bluebubbles',
  mattermost: 'sessionSources.mattermost',
  cron: 'sessionSources.cron',
}

/** 返回按当前语言翻译后的会话来源名称。 */
export function getSourceLabel(source: string | undefined, translate: (key: string) => string): string {
  if (!source) return ''
  const key = SOURCE_LABEL_KEYS[source]
  return key ? translate(key) : source
}

export function formatTimestampMs(timestamp: number): string {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

export function normalizeTimestampMs(timestamp?: number | null): number {
  if (!timestamp) return 0
  return Math.round(timestamp < 100000000000 ? timestamp * 1000 : timestamp)
}

export function formatTimestampSeconds(timestamp: number): string {
  return formatTimestampMs(timestamp * 1000)
}
