import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { computeChatContextUsage } from '../src/shared/chatContextUsage.ts'

assert.deepEqual(
  computeChatContextUsage(
    {
      inputTokens: 2485026,
      outputTokens: 49362,
      lastTotalTokens: 30933,
      contextEstimateTokens: 30933,
    },
    128000,
  ),
  {
    usedTokens: 30933,
    remainingTokens: 97067,
    usagePercent: 24.16640625,
  },
)

const chatStore = readFileSync(new URL('../src/stores/solonclaw/chat.ts', import.meta.url), 'utf8')
const runCompleted = chatStore.slice(
  chatStore.indexOf("case 'run.completed':"),
  chatStore.indexOf("case 'run.failed':"),
)
assert.match(
  runCompleted,
  /else if \(activeSessionKey\.value === sid\) \{\s+void refreshActiveSession\(\)/,
  'normal run completion should refresh the current context estimate and window',
)
assert.doesNotMatch(
  runCompleted,
  /isSlashCommand && activeSessionKey\.value === sid/,
  'context refresh must not be limited to slash commands',
)

assert.deepEqual(
  computeChatContextUsage(
    {
      inputTokens: 2485026,
      outputTokens: 49362,
      lastTotalTokens: 137500,
    },
    128000,
  ),
  {
    usedTokens: 0,
    remainingTokens: 128000,
    usagePercent: 0,
  },
)

assert.deepEqual(
  computeChatContextUsage(
    {
      inputTokens: -10,
      outputTokens: Number.NaN,
      lastTotalTokens: 0,
      contextEstimateTokens: -10,
    },
    128000,
  ),
  {
    usedTokens: 0,
    remainingTokens: 128000,
    usagePercent: 0,
  },
)
