import { createI18n } from 'vue-i18n'
import en from './locales/en'
import zh from './locales/zh'

const supportedLocales = ['zh', 'en'] as const
type SupportedLocale = typeof supportedLocales[number]

function initialLocale(): SupportedLocale {
  const saved = typeof localStorage === 'undefined' ? null : localStorage.getItem('solonclaw_locale')
  return supportedLocales.includes(saved as SupportedLocale) ? (saved as SupportedLocale) : 'zh'
}

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale(),
  fallbackLocale: 'zh',
  messages: { zh, en },
})
