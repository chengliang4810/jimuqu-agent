<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PlatformSwitchSettingRow from './PlatformSwitchSettingRow.vue'
import PlatformTextSettingRow from './PlatformTextSettingRow.vue'

type OptionalPlatform = 'wecom' | 'qqbot' | 'yuanbao'
type ChannelValues = Record<string, unknown>

interface PlatformCredentials {
  readonly enabled?: boolean
  readonly extra?: Record<string, unknown>
}

interface OptionalChannelState {
  readonly apiDomain?: string
  readonly botId?: string
  readonly websocketUrl?: string
}

interface OptionalSettingsStore {
  readonly wecom?: OptionalChannelState
  readonly qqbot: OptionalChannelState
  readonly yuanbao: OptionalChannelState
}

interface OptionalTextField {
  readonly field: string
  readonly source: 'credentials' | 'channel'
  readonly label?: string
  readonly labelKey?: string
  readonly hint?: string
  readonly hintKey?: string
  readonly placeholder?: string
  readonly placeholderKey?: string
}

const props = defineProps<{
  platform: OptionalPlatform
  settingsStore: OptionalSettingsStore
  getCreds: (platform: string) => PlatformCredentials
  isSaving: (platform: string, field: string) => boolean
  saveCredentials: (platform: string, field: string, values: ChannelValues) => Promise<void> | void
  saveChannel: (platform: string, field: string, values: ChannelValues) => Promise<void> | void
}>()

const { t } = useI18n()

const textFieldConfigs: Record<OptionalPlatform, OptionalTextField[]> = {
  wecom: [
    { field: 'bot_id', source: 'credentials', labelKey: 'platform.botId', hintKey: 'platform.botIdHint', placeholderKey: 'platform.botIdPlaceholder' },
    { field: 'secret', source: 'credentials', labelKey: 'platform.appSecret', hintKey: 'platform.wecomSecretHint', placeholderKey: 'platform.secretPlaceholder' },
  ],
  qqbot: [
    { field: 'apiDomain', source: 'channel', labelKey: 'platform.apiDomain', hintKey: 'platform.qqbotApiDomainHint', placeholder: 'https://api.sgroup.qq.com' },
    { field: 'websocketUrl', source: 'channel', labelKey: 'platform.websocketUrl', hintKey: 'platform.qqbotWebsocketHint', placeholderKey: 'platform.autoPlaceholder' },
  ],
  yuanbao: [
    { field: 'app_id', source: 'credentials', labelKey: 'platform.appId', hintKey: 'platform.yuanbaoAppIdHint', placeholderKey: 'platform.yuanbaoAppIdPlaceholder' },
    { field: 'app_secret', source: 'credentials', labelKey: 'platform.appSecret', hintKey: 'platform.yuanbaoAppSecretHint', placeholderKey: 'platform.yuanbaoAppSecretPlaceholder' },
    { field: 'botId', source: 'channel', labelKey: 'platform.botId', hintKey: 'platform.yuanbaoBotIdHint', placeholderKey: 'platform.yuanbaoBotIdPlaceholder' },
    { field: 'apiDomain', source: 'channel', labelKey: 'platform.apiDomain', hintKey: 'platform.yuanbaoApiDomainHint', placeholder: 'https://bot.yuanbao.tencent.com' },
    { field: 'websocketUrl', source: 'channel', labelKey: 'platform.websocketUrl', hintKey: 'platform.yuanbaoWebsocketHint', placeholder: 'wss://bot-wss.yuanbao.tencent.com/wss/connection' },
  ],
}

function fieldLabel(field: OptionalTextField) {
  return field.labelKey ? t(field.labelKey) : field.label || ''
}

function fieldHint(field: OptionalTextField) {
  return field.hintKey ? t(field.hintKey) : field.hint || ''
}

function fieldPlaceholder(field: OptionalTextField) {
  return field.placeholderKey ? t(field.placeholderKey) : field.placeholder || ''
}

function fieldValue(field: OptionalTextField) {
  if (field.source === 'credentials') {
    return String(props.getCreds(props.platform).extra?.[field.field] || '')
  }
  return String(props.settingsStore[props.platform]?.[field.field as keyof OptionalChannelState] || '')
}

function saveTextField(field: OptionalTextField, value: string) {
  if (field.source === 'credentials') {
    return props.saveCredentials(props.platform, field.field, {
      extra: { ...props.getCreds(props.platform).extra, [field.field]: value },
    })
  }
  return props.saveChannel(props.platform, field.field, { [field.field]: value })
}
</script>

<template>
  <PlatformSwitchSettingRow :label="t('platform.channelEnabled')" :hint="t('platform.channelEnabledHint')" :value="Boolean(getCreds(platform).enabled)" :loading="isSaving(platform, 'enabled')" @change="v => saveCredentials(platform, 'enabled', { enabled: v })" />
  <PlatformTextSettingRow v-for="field in textFieldConfigs[platform]" :key="`${field.source}:${field.field}`" :label="fieldLabel(field)" :hint="fieldHint(field)" :value="fieldValue(field)" :loading="isSaving(platform, field.field)" :placeholder="fieldPlaceholder(field)" @change="v => saveTextField(field, v)" />
</template>
