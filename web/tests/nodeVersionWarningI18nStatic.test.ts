import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import en from '../src/i18n/locales/en.ts'
import zh from '../src/i18n/locales/zh.ts'

const app = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')

assert.ok(app.includes("t('sidebar.nodeVersionWarning'"))

for (const [locale, messages] of Object.entries({ en, zh })) {
  assert.equal(
    typeof messages.sidebar.nodeVersionWarning,
    'string',
    `${locale} should translate sidebar.nodeVersionWarning`,
  )
  assert.match(messages.sidebar.nodeVersionWarning, /\{version\}/)
}
