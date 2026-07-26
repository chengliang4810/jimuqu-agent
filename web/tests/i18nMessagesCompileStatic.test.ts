import assert from 'node:assert/strict'
import { createI18n } from 'vue-i18n'
import en from '../src/i18n/locales/en.ts'
import zh from '../src/i18n/locales/zh.ts'

const locales = { zh, en }

function messageKeys(value: unknown, prefix = ''): string[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return []
  }
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key
    return child && typeof child === 'object' && !Array.isArray(child)
      ? messageKeys(child, path)
      : [path]
  })
}

for (const [locale, messages] of Object.entries(locales)) {
  const i18n = createI18n({
    legacy: false,
    locale,
    messages: { [locale]: messages },
  })
  const errors: string[] = []

  for (const key of messageKeys(messages)) {
    try {
      i18n.global.t(key)
    } catch (error) {
      errors.push(`${locale}.${key}: ${(error as Error).message.split('\n')[0]}`)
    }
  }

  assert.deepEqual(errors, [])
}
