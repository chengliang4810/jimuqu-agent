import assert from 'node:assert/strict'

import en from '../src/i18n/locales/en.ts'
import zh from '../src/i18n/locales/zh.ts'

const locales = { en, zh }

for (const [locale, messages] of Object.entries(locales)) {
  const description = messages.login.description
  assert.ok(
    description.includes('solonclaw.dashboard.accessToken'),
    `${locale} login description should point users to the dashboard access token config key`,
  )
}
