import assert from 'node:assert/strict'
import { normalizePlatformSettingsItems } from '../src/components/solonclaw/settings/platformDefinitions.ts'

const catalog = [
  { code: 'yuanbao', displayName: '元宝助理', iconKey: 'yuanbao', order: 5 },
  { code: 'dingtalk', displayName: '钉钉工作台', iconKey: 'dingtalk', order: 9 },
  { code: 'feishu', displayName: '飞书入口', iconKey: 'feishu', order: 10 },
  { code: 'telegram', displayName: 'Telegram', iconKey: 'telegram', order: 1 },
  { code: 'qqbot', displayName: 'QQ 频道', iconKey: 'missing', order: 15 },
  { code: 'wecom', displayName: '企业微信', iconKey: 'wecom', order: 30 },
  { code: 'weixin', enabled: false, order: 20 },
] as const

const items = normalizePlatformSettingsItems(catalog)

assert.deepEqual(
  items.map(item => item.key),
  ['yuanbao', 'dingtalk', 'feishu', 'qqbot', 'wecom'],
  'platform catalog should sort confirmed domestic channels and ignore disabled or unsupported items',
)
assert.equal(items[0]?.name, '元宝助理', 'platform catalog should fall back to backend names without a translator')
const qqbot = items.find(item => item.key === 'qqbot')
assert.equal(qqbot?.name, 'QQ 频道', 'platform catalog should keep backend names when translation is unavailable')
assert.ok(qqbot?.icon.includes('<svg'), 'platform catalog should resolve icons from local SVG mapping')

const englishLabels: Record<string, string> = {
  'platform.nameFeishu': 'Feishu',
  'platform.nameDingtalk': 'DingTalk',
  'platform.nameWecom': 'WeCom',
  'platform.nameWeixin': 'WeChat',
  'platform.nameQqbot': 'QQ Bot',
  'platform.nameYuanbao': 'Yuanbao',
}
const englishItems = normalizePlatformSettingsItems(catalog, key => englishLabels[key] || key)
assert.deepEqual(
  englishItems.map(item => item.name),
  ['Yuanbao', 'DingTalk', 'Feishu', 'QQ Bot', 'WeCom'],
  'known channel names should follow the active English locale instead of backend Chinese metadata',
)

const chineseLabels: Record<string, string> = {
  'platform.nameFeishu': '飞书',
  'platform.nameDingtalk': '钉钉',
  'platform.nameWecom': '企业微信',
  'platform.nameWeixin': '微信',
  'platform.nameQqbot': 'QQ Bot',
  'platform.nameYuanbao': '腾讯元宝',
}
const chineseItems = normalizePlatformSettingsItems(catalog, key => chineseLabels[key] || key)
assert.deepEqual(
  chineseItems.map(item => item.name),
  ['腾讯元宝', '钉钉', '飞书', 'QQ Bot', '企业微信'],
  'known channel names should follow the active Chinese locale',
)
