import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const loginView = readFileSync(new URL('../src/views/LoginView.vue', import.meta.url), 'utf8')
const router = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')

assert.ok(
  loginView.includes('async function validateExistingToken()'),
  'login page should validate an injected token or HttpOnly session before leaving the login page',
)
assert.ok(
  loginView.includes('await exchangeDashboardSession(urlToken)'),
  'login page should exchange a URL token for a short HttpOnly session',
)
assert.ok(
  loginView.includes('await restoreDashboardSession()'),
  'login page should restore an existing HttpOnly session when no URL token is present',
)
assert.ok(
  loginView.includes('onMounted(async () =>'),
  'login page should validate authentication during setup',
)
assert.ok(
  /async function validateExistingToken\(\)[\s\S]*if \(authenticated\)[\s\S]*router\.replace\(loginTarget\(\)\)/.test(loginView),
  'successful short-session validation should route into the requested page',
)
assert.ok(
  !loginView.includes('if (hasApiKey()) {\n  router.replace("/chat");\n}'),
  'login page must not route to chat only because a token exists locally',
)
assert.ok(
  loginView.includes('route.redirectedFrom'),
  'login page should preserve the guarded route that sent the user to login',
)
assert.ok(
  router.includes("query: { redirect: to.fullPath }"),
  'router guard should pass direct dashboard targets to login explicitly',
)
assert.ok(
  loginView.includes("route.query.redirect"),
  'login page should restore the explicit redirect query after token validation',
)
assert.ok(
  !loginView.includes('router.replace("/chat")'),
  'login page should not hardcode chat as every successful login target',
)
assert.ok(
  loginView.includes('clearApiKey()'),
  'failed session validation should clear stale local and injected authentication state',
)
assert.ok(
  !loginView.includes('localStorage.setItem'),
  'login page must not persist the long-lived Dashboard token',
)
assert.ok(
  !router.includes("if (to.name === 'login' && hasApiKey())"),
  'router guard must not skip the login page only because a token exists locally',
)
