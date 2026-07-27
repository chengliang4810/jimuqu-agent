import assert from 'node:assert/strict'

const store = new Map<string, string>()
const windowListeners = new Map<string, Set<(event: { key: string | null }) => void>>()
const fetchCalls: Array<{ input: string, options: RequestInit }> = []
let nextFetchStatus = 200
let nextFetchError: Error | null = null

Object.defineProperty(globalThis, 'localStorage', {
  value: {
    get length() { return store.size },
    getItem: (key: string) => store.get(key) || null,
    key: (index: number) => [...store.keys()][index] || null,
    setItem: (key: string, value: string) => store.set(key, value),
    removeItem: (key: string) => store.delete(key),
  },
  configurable: true,
})

Object.defineProperty(globalThis, 'window', {
  value: {
    __LOGIN_TOKEN__: 'url-token',
    addEventListener: (type: string, listener: (event: { key: string | null }) => void) => {
      const listeners = windowListeners.get(type) || new Set()
      listeners.add(listener)
      windowListeners.set(type, listeners)
    },
    location: {
      hostname: '127.0.0.1',
      origin: 'http://127.0.0.1:5173',
    },
  },
  configurable: true,
})

Object.defineProperty(globalThis, 'fetch', {
  value: async (input: RequestInfo | URL, options: RequestInit = {}) => {
    fetchCalls.push({ input: String(input), options })
    if (nextFetchError) throw nextFetchError
    return { ok: nextFetchStatus >= 200 && nextFetchStatus < 300, status: nextFetchStatus } as Response
  },
  configurable: true,
})

localStorage.setItem('solonclaw_api_key', 'legacy-long-lived-token')

const {
  clearApiKey,
  exchangeDashboardSession,
  getAuthScopeId,
  getBaseUrlValue,
  getInjectedToken,
  hasApiKey,
  logoutDashboardSession,
  onAuthContextChange,
  restoreDashboardSession,
  setServerUrl,
} = await import('../src/api/sessionAuth.ts')
const { chatCacheKey } = await import('../src/shared/chatCacheScope.ts')

assert.equal(
  localStorage.getItem('solonclaw_api_key'),
  null,
  'module initialization should purge a legacy long-lived Bearer from localStorage',
)

let authChanges = 0
onAuthContextChange(() => { authChanges += 1 })
localStorage.setItem('solonclaw_sessions_cache_v2', 'old-account-data')

assert.equal(await exchangeDashboardSession('long-lived-token'), true, 'valid Bearer should establish a short session')
assert.equal(fetchCalls[0]?.input, '/api/auth/session')
assert.equal(fetchCalls[0]?.options.method, 'POST')
assert.equal(fetchCalls[0]?.options.credentials, 'same-origin')
assert.deepEqual(fetchCalls[0]?.options.headers, { Authorization: 'Bearer long-lived-token' })
assert.equal(getInjectedToken(), '', 'successful exchange should clear the transient URL token')
assert.equal(hasApiKey(), true, 'successful exchange should mark the server session active in memory')
assert.equal(
  [...store.entries()].some(([key, value]) => key.includes('api_key') || value.includes('long-lived-token')),
  false,
  'long-lived Bearer must never be written to localStorage',
)
assert.equal(localStorage.getItem('solonclaw_sessions_cache_v2'), null, 'login should clear old auth-scoped chat data')
assert.ok(chatCacheKey('', getAuthScopeId(), 'sessions'), 'authenticated session should receive a non-secret cache namespace')
assert.equal(authChanges, 1, 'login should notify in-memory auth-scoped stores')

clearApiKey()
assert.equal(hasApiKey(), false, 'local auth failure should clear the in-memory session marker')
assert.equal(getInjectedToken(), '', 'local auth failure should keep the transient URL token cleared')

assert.equal(await restoreDashboardSession(), true, 'existing HttpOnly Cookie should restore the current tab')
assert.equal(fetchCalls.at(-1)?.options.method, 'GET')
assert.equal(fetchCalls.at(-1)?.options.credentials, 'same-origin')
assert.equal((fetchCalls.at(-1)?.options.headers as Record<string, string> | undefined)?.Authorization, undefined)
assert.equal(hasApiKey(), true)

await logoutDashboardSession()
assert.equal(fetchCalls.at(-1)?.options.method, 'DELETE')
assert.equal(fetchCalls.at(-1)?.options.credentials, 'same-origin')
assert.equal(hasApiKey(), false, 'logout should clear local session state after server revocation')

nextFetchStatus = 401
assert.equal(await exchangeDashboardSession('invalid-token'), false, 'invalid Bearer should not establish a session')
assert.equal(hasApiKey(), false)
assert.equal(await restoreDashboardSession(), false, 'expired HttpOnly Cookie should not restore a session')

nextFetchStatus = 503
await assert.rejects(
  () => exchangeDashboardSession('server-failure-token'),
  /exchange failed with HTTP 503/,
  'server failures must not be reported as invalid credentials',
)
await assert.rejects(
  () => restoreDashboardSession(),
  /restore failed with HTTP 503/,
  'session restore failures must remain distinguishable from an expired session',
)

nextFetchStatus = 200
await exchangeDashboardSession('second-account')
nextFetchError = new Error('network unavailable')
await assert.rejects(
  () => logoutDashboardSession(),
  /network unavailable/,
  'network failure must not pretend that the server-side session was revoked',
)
assert.equal(hasApiKey(), true, 'failed logout should preserve the active local session marker')
nextFetchError = null
localStorage.setItem('solonclaw_sessions_cache_v2', 'other-tab-old-data')
for (const listener of windowListeners.get('storage') || []) {
  listener({ key: 'solonclaw_auth_scope_v1' })
}
assert.equal(hasApiKey(), false, 'another tab auth switch should invalidate this tab marker')
assert.equal(localStorage.getItem('solonclaw_sessions_cache_v2'), null, 'another tab auth switch should clear old chat data')

setServerUrl('http://127.0.0.1:8080')
assert.equal(getBaseUrlValue(), '', 'stale localhost server URL should not override the current dashboard origin')

setServerUrl('https://dashboard.example.com')
assert.equal(getBaseUrlValue(), 'https://dashboard.example.com', 'non-local server URL should stay configurable')
