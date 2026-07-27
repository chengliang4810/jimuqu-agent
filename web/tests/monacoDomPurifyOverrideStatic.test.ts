import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const packageJson = JSON.parse(
  readFileSync(new URL('../package.json', import.meta.url), 'utf8'),
)
const packageLock = JSON.parse(
  readFileSync(new URL('../package-lock.json', import.meta.url), 'utf8'),
)
const viteConfig = readFileSync(new URL('../vite.config.ts', import.meta.url), 'utf8')
const secureDomPurify = readFileSync(
  new URL('../node_modules/dompurify/dist/purify.es.mjs', import.meta.url),
  'utf8',
)

assert.equal(
  packageJson.overrides?.['monaco-editor']?.dompurify,
  '3.4.12',
  'Monaco should resolve the reviewed DOMPurify security release',
)
assert.equal(
  packageLock.packages?.['node_modules/dompurify']?.version,
  '3.4.12',
  'the lock file should pin the reviewed DOMPurify security release',
)
assert.ok(
  viteConfig.includes("node_modules/dompurify/dist/purify.es.mjs")
    && viteConfig.includes('/^\\.\\/dompurify\\/dompurify\\.js$/'),
  'Vite should replace the DOMPurify copy vendored by Monaco',
)
assert.ok(
  secureDomPurify.includes('DOMPurify 3.4.12')
    && secureDomPurify.includes("DOMPurify.version = '3.4.12'"),
  'the replacement module should contain DOMPurify 3.4.12',
)
