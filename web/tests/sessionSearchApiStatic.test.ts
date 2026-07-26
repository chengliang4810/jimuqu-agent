import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const api = readFileSync(new URL('../src/api/solonclaw/sessions.ts', import.meta.url), 'utf8')

assert.ok(api.includes('/api/search?'), 'session search should call the backend search endpoint')
assert.ok(!api.includes('/api/sessions/search'), 'session search should not call a missing sessions search endpoint')
assert.ok(
  api.includes('function sessionPath(id: string, profile?: string)'),
  'session path parameters should use one encoding helper while preserving the Profile scope',
)
assert.ok(api.includes('function checkpointPath(id: string)'), 'checkpoint path parameters should use one encoding helper')
assert.ok(!api.includes('/api/sessions/${id}'), 'sessions API should not interpolate raw session id path segments')
assert.ok(!api.includes('/api/checkpoints/${id}'), 'sessions API should not interpolate raw checkpoint id path segments')
assert.ok(api.includes('match_preview'), 'session search should map the backend match preview field')
assert.ok(api.includes('updated_at'), 'session search should map the backend updated timestamp')
assert.ok(
  api.includes("params.set('conversation_only', 'true')"),
  'session search should ask the backend to apply the shared user-conversation visibility rule',
)
assert.ok(
  api.includes("params.set('profile', 'all')"),
  'session search should aggregate all Profiles after removing the global Profile switcher',
)
assert.ok(
  api.includes('profileSessionIdentity(item.session_id, item.profile || undefined)'),
  'session search metadata should use Profile plus session id as its unique key',
)
assert.ok(
  !api.includes('.filter(item => map.has(item.session_id))'),
  'session search should not limit valid older matches to the recently loaded metadata window',
)
assert.ok(
  !api.includes("request<{ sessions: DashboardSessionSummary[] }>('/api/sessions?limit=500&offset=0')"),
  'fetchSession should not load the 500-row session list for single-session details',
)
