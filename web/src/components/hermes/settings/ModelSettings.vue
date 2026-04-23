<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { NInput, NButton, NSpin, NEmpty, NSelect, useMessage } from 'naive-ui'
import { useModelsStore } from '@/stores/hermes/models'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const modelsStore = useModelsStore()
const message = useMessage()

const savingKey = ref<string | null>(null)
const defaultProvider = ref('')
const defaultModel = ref('')
const fallbackRows = ref<Array<{ provider: string; model: string }>>([])
const providerForms = ref<Record<string, {
  name: string
  baseUrl: string
  apiKey: string
  defaultModel: string
  dialect: string
}>>({})

const providerOptions = computed(() =>
  modelsStore.providers.map(provider => ({
    label: provider.label,
    value: provider.provider,
  })),
)

function dialectLabel(value: string): string {
  switch (value) {
    case 'openai':
      return t('models.dialectOpenai')
    case 'openai-responses':
      return t('models.dialectOpenaiResponses')
    case 'ollama':
      return t('models.dialectOllama')
    case 'gemini':
      return t('models.dialectGemini')
    case 'anthropic':
      return t('models.dialectAnthropic')
    default:
      return value
  }
}

const dialectOptions = [
  { label: dialectLabel('openai'), value: 'openai' },
  { label: dialectLabel('openai-responses'), value: 'openai-responses' },
  { label: dialectLabel('ollama'), value: 'ollama' },
  { label: dialectLabel('gemini'), value: 'gemini' },
  { label: dialectLabel('anthropic'), value: 'anthropic' },
]

function syncForms() {
  defaultProvider.value = modelsStore.defaultProvider
  defaultModel.value = modelsStore.defaultModel
  fallbackRows.value = modelsStore.fallbackProviders.map(item => ({
    provider: item.provider,
    model: item.model,
  }))

  const next: Record<string, {
    name: string
    baseUrl: string
    apiKey: string
    defaultModel: string
    dialect: string
  }> = {}
  for (const provider of modelsStore.providers) {
    next[provider.provider] = {
      name: provider.label,
      baseUrl: provider.base_url,
      apiKey: '',
      defaultModel: provider.models[0] || '',
      dialect: provider.dialect,
    }
  }
  providerForms.value = next
}

onMounted(async () => {
  if (modelsStore.providers.length === 0) {
    await modelsStore.fetchProviders()
  }
  syncForms()
})

watch(
  () => [modelsStore.providers, modelsStore.defaultProvider, modelsStore.defaultModel, modelsStore.fallbackProviders],
  () => syncForms(),
  { deep: true },
)

async function handleSaveDefault() {
  if (!defaultProvider.value) {
    message.warning(t('models.selectProviderRequired'))
    return
  }
  savingKey.value = 'default'
  try {
    await modelsStore.setDefaultModel(defaultModel.value.trim(), defaultProvider.value)
    message.success(t('settings.models.saved'))
  } catch (e: any) {
    message.error(e.message || t('settings.models.saveFailed'))
  } finally {
    savingKey.value = null
  }
}

async function handleSaveProvider(providerKey: string) {
  const form = providerForms.value[providerKey]
  savingKey.value = providerKey
  try {
    await modelsStore.updateProvider(providerKey, {
      name: form.name.trim(),
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey,
      defaultModel: form.defaultModel.trim(),
      dialect: form.dialect,
    })
    message.success(t('settings.models.saved'))
  } catch (e: any) {
    message.error(e.message || t('settings.models.saveFailed'))
  } finally {
    savingKey.value = null
  }
}

function addFallbackRow() {
  fallbackRows.value.push({ provider: '', model: '' })
}

function removeFallbackRow(index: number) {
  fallbackRows.value.splice(index, 1)
}

async function handleSaveFallbacks() {
  const cleaned = fallbackRows.value
    .filter(item => item.provider)
    .map(item => ({
      provider: item.provider,
      model: item.model.trim(),
    }))

  savingKey.value = 'fallbacks'
  try {
    await modelsStore.saveFallbackProviders(cleaned)
    message.success(t('settings.models.saved'))
  } catch (e: any) {
    message.error(e.message || t('settings.models.saveFailed'))
  } finally {
    savingKey.value = null
  }
}
</script>

<template>
  <section class="settings-section">
    <NSpin :show="modelsStore.loading">
      <div v-if="modelsStore.providers.length === 0" class="empty-hint">
        <NEmpty :description="t('settings.models.noProviders')" />
      </div>

      <template v-else>
        <div class="panel">
          <div class="panel-header">
            <h4>{{ t('models.defaultBadge') }}</h4>
          </div>
          <div class="field-grid">
            <NSelect
              v-model:value="defaultProvider"
              :options="providerOptions"
              :placeholder="t('models.chooseProvider')"
            />
            <NInput
              v-model:value="defaultModel"
              :placeholder="t('models.defaultModel')"
            />
            <NButton
              type="primary"
              :loading="savingKey === 'default'"
              @click="handleSaveDefault"
            >
              {{ t('settings.models.save') }}
            </NButton>
          </div>
        </div>

        <div class="panel">
          <div class="panel-header">
            <h4>{{ t('models.fallbackProviders') }}</h4>
            <NButton size="small" secondary @click="addFallbackRow">{{ t('common.add') }}</NButton>
          </div>
          <div v-if="fallbackRows.length === 0" class="empty-inline">
            {{ t('models.noFallbackProviders') }}
          </div>
          <div v-for="(row, index) in fallbackRows" :key="index" class="fallback-row">
            <NSelect
              v-model:value="row.provider"
              :options="providerOptions"
              :placeholder="t('models.chooseProvider')"
            />
            <NInput
              v-model:value="row.model"
              :placeholder="t('models.optionalModelOverride')"
            />
            <NButton quaternary type="error" @click="removeFallbackRow(index)">
              {{ t('common.delete') }}
            </NButton>
          </div>
          <div class="actions">
            <NButton
              type="primary"
              :loading="savingKey === 'fallbacks'"
              @click="handleSaveFallbacks"
            >
              {{ t('settings.models.save') }}
            </NButton>
          </div>
        </div>

        <div v-for="provider in modelsStore.providers" :key="provider.provider" class="panel">
          <div class="panel-header">
            <h4>{{ provider.label }}</h4>
            <span class="dialect-tag">{{ dialectLabel(provider.dialect) }}</span>
          </div>
          <div class="provider-grid">
            <NInput
              v-model:value="providerForms[provider.provider].name"
              :placeholder="t('models.name')"
            />
            <NInput
              v-model:value="providerForms[provider.provider].baseUrl"
              :placeholder="t('models.baseUrl')"
            />
            <NInput
              v-model:value="providerForms[provider.provider].defaultModel"
              :placeholder="t('models.defaultModel')"
            />
            <NSelect
              v-model:value="providerForms[provider.provider].dialect"
              :options="dialectOptions"
            />
            <NInput
              v-model:value="providerForms[provider.provider].apiKey"
              type="password"
              show-password-on="click"
              :placeholder="provider.has_api_key ? t('models.apiKeyConfigured') : t('settings.models.apiKeyPlaceholder')"
              autocomplete="off"
            />
            <NButton
              type="primary"
              :loading="savingKey === provider.provider"
              @click="handleSaveProvider(provider.provider)"
            >
              {{ t('settings.models.save') }}
            </NButton>
          </div>
        </div>
      </template>
    </NSpin>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.settings-section {
  margin-top: 16px;
}

.empty-hint {
  padding: 40px 0;
}

.panel {
  border: 1px solid $border-color;
  border-radius: $radius-md;
  padding: 16px;
  margin-bottom: 16px;
  background: $bg-card;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;

  h4 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
  }
}

.field-grid,
.provider-grid,
.fallback-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.provider-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.fallback-row + .fallback-row {
  margin-top: 10px;
}

.dialect-tag {
  font-size: 12px;
  color: $text-muted;
}

.empty-inline {
  font-size: 13px;
  color: $text-muted;
}

.actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
