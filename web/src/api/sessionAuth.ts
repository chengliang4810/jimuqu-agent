import { clearChatCacheStorage, newCacheScopeId } from '../shared/chatCacheScope.ts'

declare global {
  interface Window {
    __LOGIN_TOKEN__?: string
  }
}

const DEFAULT_BASE_URL = ''
const LEGACY_TOKEN_KEY = 'solonclaw_api_key'
const SERVER_URL_KEY = 'solonclaw_server_url'
const AUTH_SCOPE_KEY = 'solonclaw_auth_scope_v1'
const SESSION_ENDPOINT = '/api/auth/session'
const authContextListeners = new Set<() => void>()
let sessionAuthenticated = false

/** 注册认证上下文变化监听器，供内存 Store 同步清理旧主体数据。 */
export function onAuthContextChange(listener: () => void): () => void {
  authContextListeners.add(listener)
  return () => authContextListeners.delete(listener)
}

/** 先通知内存状态停止活动任务，再删除所有不再可信的聊天缓存。 */
function invalidateAuthContext(): void {
  for (const listener of authContextListeners) {
    try {
      listener()
    } catch (error) {
      console.error('Failed to reset auth-scoped state:', error)
    }
  }
  clearChatCacheStorage()
}

/** 其他标签页切换认证作用域或服务端时，立即废弃本页仍在运行的旧主体状态。 */
function handleAuthStorageChange(event: StorageEvent): void {
  if (event.key === AUTH_SCOPE_KEY || event.key === SERVER_URL_KEY || event.key === null) {
    sessionAuthenticated = false
    invalidateAuthContext()
  }
}

if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
  window.addEventListener('storage', handleAuthStorageChange)
}

/** 删除旧版本曾持久化的 Dashboard 长期令牌，完成升级迁移后不再保留明文。 */
function purgeLegacyBearerToken(): void {
  try {
    localStorage.removeItem(LEGACY_TOKEN_KEY)
  } catch {
    // 浏览器禁用存储时无需阻塞短会话认证。
  }
}

purgeLegacyBearerToken()

/** 判断主机名是否指向本机回环地址。 */
function isLoopbackHost(hostname: string): boolean {
  const host = hostname.toLowerCase()
  return host === 'localhost' || host === '::1' || host === '[::1]' || host.startsWith('127.')
}

/** 判断 URL 是否指向本机回环地址。 */
function isLoopbackUrl(value: string): boolean {
  try {
    return isLoopbackHost(new URL(value).hostname)
  } catch {
    return false
  }
}

/** 返回当前 Dashboard API 基础地址。 */
export function getBaseUrlValue(): string {
  const stored = localStorage.getItem(SERVER_URL_KEY) || DEFAULT_BASE_URL
  if (stored && isLoopbackHost(window.location.hostname) && isLoopbackUrl(stored)) {
    return DEFAULT_BASE_URL
  }
  return stored
}

/** 返回仅在页面初始化期间存在的 URL 登录令牌。 */
export function getInjectedToken(): string {
  return window.__LOGIN_TOKEN__ || ''
}

/** 返回当前浏览器是否已建立服务端 HttpOnly 短会话。 */
export function hasApiKey(): boolean {
  return sessionAuthenticated
}

/** 返回当前认证主体的非秘密缓存作用域；未认证或存储不可用时禁用聊天缓存。 */
export function getAuthScopeId(): string {
  if (!sessionAuthenticated) return ''
  try {
    const existing = localStorage.getItem(AUTH_SCOPE_KEY)
    if (existing) return existing
    const created = newCacheScopeId()
    localStorage.setItem(AUTH_SCOPE_KEY, created)
    return localStorage.getItem(AUTH_SCOPE_KEY) || created
  } catch {
    return ''
  }
}

/** 更新 Dashboard API 基础地址，并废弃旧服务端的认证上下文。 */
export function setServerUrl(url: string): void {
  const previous = getBaseUrlValue()
  localStorage.setItem(SERVER_URL_KEY, url)
  if (getBaseUrlValue() !== previous) {
    sessionAuthenticated = false
    invalidateAuthContext()
  }
}

/** 使用长期 Bearer 一次性换取 HttpOnly 短会话，长期令牌不会写入浏览器存储。 */
export async function exchangeDashboardSession(key: string): Promise<boolean> {
  const token = key.trim()
  if (!token) return false
  try {
    const response = await fetch(`${getBaseUrlValue()}${SESSION_ENDPOINT}`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { Authorization: `Bearer ${token}` },
    })
    if (response.status === 401) return false
    if (!response.ok) {
      throw new Error(`Dashboard session exchange failed with HTTP ${response.status}`)
    }
    activateDashboardSession(true)
    return true
  } finally {
    window.__LOGIN_TOKEN__ = ''
  }
}

/** 尝试使用现有 HttpOnly Cookie 恢复当前标签页的 Dashboard 短会话。 */
export async function restoreDashboardSession(): Promise<boolean> {
  const response = await fetch(`${getBaseUrlValue()}${SESSION_ENDPOINT}`, {
    method: 'GET',
    credentials: 'same-origin',
  })
  if (response.status === 401) {
    clearApiKey()
    return false
  }
  if (!response.ok) {
    throw new Error(`Dashboard session restore failed with HTTP ${response.status}`)
  }
  activateDashboardSession(false)
  return true
}

/** 撤销服务端短会话；只有服务端确认撤销或会话已失效后才清除本地认证状态。 */
export async function logoutDashboardSession(): Promise<void> {
  const response = await fetch(`${getBaseUrlValue()}${SESSION_ENDPOINT}`, {
    method: 'DELETE',
    credentials: 'same-origin',
  })
  if (!response.ok && response.status !== 401) {
    throw new Error(`Dashboard session logout failed with HTTP ${response.status}`)
  }
  clearApiKey()
}

/** 清除本标签页认证状态和所有认证作用域缓存，不保留任何长期令牌。 */
export function clearApiKey(): void {
  sessionAuthenticated = false
  invalidateAuthContext()
  try {
    localStorage.removeItem(LEGACY_TOKEN_KEY)
    localStorage.removeItem(AUTH_SCOPE_KEY)
  } finally {
    window.__LOGIN_TOKEN__ = ''
  }
}

/** 激活已由服务端确认的短会话，并按登录类型维护非秘密缓存作用域。 */
function activateDashboardSession(rotateScope: boolean): void {
  const changed = !sessionAuthenticated || rotateScope
  sessionAuthenticated = true
  if (rotateScope) {
    try {
      localStorage.removeItem(AUTH_SCOPE_KEY)
      localStorage.setItem(AUTH_SCOPE_KEY, newCacheScopeId())
    } catch {
      // 缓存作用域是辅助数据；写入失败时清旧状态并让后续读取自然降级为无缓存。
    }
  }
  if (changed) {
    invalidateAuthContext()
  } else {
    getAuthScopeId()
  }
}
