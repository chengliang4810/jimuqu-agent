import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

/** 运行记录页面源码，用于锁定三栏局部滚动所需的高度约束。 */
const view = readFileSync(new URL('../src/views/solonclaw/RunsView.vue', import.meta.url), 'utf8')

assert.ok(
  view.includes('.runs-view :deep(.ant-spin),'),
  'RunsView should constrain the actual antdv-next Spin wrapper',
)
assert.match(
  view,
  /\.runs-layout\s*\{[\s\S]*?grid-template-rows:\s*minmax\(0,\s*1fr\);[\s\S]*?overflow:\s*hidden;/,
  'desktop run panels should share one bounded grid row instead of expanding the page',
)
assert.match(
  view,
  /@media \(max-width: 1100px\)[\s\S]*?\.runs-layout\s*\{[\s\S]*?grid-template-rows:\s*none;[\s\S]*?grid-auto-rows:\s*clamp\(320px,\s*60vh,\s*560px\);[\s\S]*?overflow-y:\s*auto;/,
  'narrow layouts should give each panel one bounded non-overlapping row',
)
