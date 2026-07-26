import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const i18nIndex = readFileSync(new URL('../src/i18n/index.ts', import.meta.url), 'utf8')
const languageSwitch = readFileSync(new URL('../src/components/layout/LanguageSwitch.vue', import.meta.url), 'utf8')
const appSidebar = readFileSync(new URL('../src/components/layout/AppSidebar.vue', import.meta.url), 'utf8')
const app = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
const themeSwitch = readFileSync(new URL('../src/components/layout/ThemeSwitch.vue', import.meta.url), 'utf8')

for (const locale of ['zh', 'en']) {
  assert.ok(
    languageSwitch.includes(`value: '${locale}'`),
    `language switch should expose ${locale}`,
  )
  assert.ok(
    i18nIndex.includes(`import ${locale} from './locales/${locale}'`),
    `i18n should import ${locale} locale messages`,
  )
  assert.ok(
    i18nIndex.includes('messages: { zh, en }'),
    `i18n should register ${locale} locale messages`,
  )
}

for (const locale of ['ja', 'ko', 'fr', 'es', 'de', 'pt']) {
  assert.ok(!languageSwitch.includes(`value: '${locale}'`), `language switch should not expose ${locale}`)
  assert.ok(!i18nIndex.includes(`./locales/${locale}`), `i18n should not import ${locale} locale messages`)
}

assert.ok(
  i18nIndex.includes("localStorage.getItem('solonclaw_locale')"),
  'i18n should restore a supported persisted locale',
)
assert.ok(
  i18nIndex.includes("supportedLocales.includes(saved as SupportedLocale)") && i18nIndex.includes(": 'zh'"),
  'unsupported or missing persisted locales should fall back to Chinese',
)
assert.ok(
  appSidebar.includes('LanguageSwitch') && appSidebar.includes('<LanguageSwitch'),
  'AppSidebar should expose the language switch next to persistent shell controls',
)
assert.ok(app.includes("t('sidebar.openMenu')"), 'the mobile menu control should use an i18n label')
assert.ok(
  themeSwitch.includes("t('theme.lightMode')") && themeSwitch.includes("t('theme.darkMode')"),
  'the theme switch should use translated accessible labels',
)
