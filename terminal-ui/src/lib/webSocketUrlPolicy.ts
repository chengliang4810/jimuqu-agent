/** 将 URL hostname 规范化为可安全比较的形式。 */
const normalizeHostname = (hostname: string): string => {
  const lower = hostname.toLowerCase()
  const withoutBrackets = lower.startsWith('[') && lower.endsWith(']') ? lower.slice(1, -1) : lower

  return withoutBrackets.endsWith('.') ? withoutBrackets.slice(0, -1) : withoutBrackets
}

/** 判断规范化后的 hostname 是否明确指向本机 loopback。 */
const isLoopbackHostname = (hostname: string): boolean => {
  const normalized = normalizeHostname(hostname)

  return normalized === 'localhost' || normalized === '::1' || /^127(?:\.\d{1,3}){3}$/.test(normalized)
}

/**
 * 拒绝不安全的 WebSocket 目标。
 *
 * 远程连接必须使用 TLS；只有明确的 IPv4、IPv6 或 localhost loopback
 * 才允许使用明文 WebSocket，避免会话数据和 Provider 凭据离开本机时被窃听。
 */
export const assertSecureWebSocketUrl = (raw: string): void => {
  let url: URL

  try {
    url = new URL(raw)
  } catch {
    throw new Error('WebSocket URL 格式无效')
  }

  if (url.protocol === 'wss:') {
    return
  }

  if (url.protocol === 'ws:' && isLoopbackHostname(url.hostname)) {
    return
  }

  if (url.protocol === 'ws:') {
    throw new Error('拒绝连接远程明文 WebSocket；请改用 wss: 或 SSH loopback 隧道')
  }

  throw new Error('WebSocket URL 必须使用 ws: 或 wss:')
}
