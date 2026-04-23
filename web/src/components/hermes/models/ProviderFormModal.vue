<script setup lang="ts">
import { ref } from 'vue'
import { NModal, NForm, NFormItem, NInput, NButton, NSelect, useMessage } from 'naive-ui'
import { useModelsStore } from '@/stores/hermes/models'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const modelsStore = useModelsStore()
const message = useMessage()

const showModal = ref(true)
const loading = ref(false)
const formData = ref({
  providerKey: '',
  name: '',
  baseUrl: '',
  apiKey: '',
  defaultModel: '',
  dialect: 'openai-responses',
})

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

async function handleSave() {
  if (!formData.value.providerKey.trim()) {
    message.warning(t('models.providerKeyRequired'))
    return
  }
  if (!formData.value.name.trim()) {
    message.warning(t('models.nameRequired'))
    return
  }
  if (!formData.value.baseUrl.trim()) {
    message.warning(t('models.baseUrlRequired'))
    return
  }
  if (!formData.value.defaultModel.trim()) {
    message.warning(t('models.modelRequired'))
    return
  }

  loading.value = true
  try {
    await modelsStore.addProvider({
      providerKey: formData.value.providerKey.trim(),
      name: formData.value.name.trim(),
      baseUrl: formData.value.baseUrl.trim(),
      apiKey: formData.value.apiKey.trim(),
      defaultModel: formData.value.defaultModel.trim(),
      dialect: formData.value.dialect,
    })
    message.success(t('models.providerAdded'))
    emit('saved')
  } catch (e: any) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

function handleClose() {
  showModal.value = false
  setTimeout(() => emit('close'), 200)
}
</script>

<template>
  <NModal
    v-model:show="showModal"
    preset="card"
    :title="t('models.addProvider')"
    :style="{ width: 'min(560px, calc(100vw - 32px))' }"
    :mask-closable="!loading"
    @after-leave="emit('close')"
  >
    <NForm label-placement="top">
      <NFormItem :label="t('models.providerKey')" required>
        <NInput
          v-model:value="formData.providerKey"
          :placeholder="t('models.providerKeyPlaceholder')"
        />
      </NFormItem>

      <NFormItem :label="t('models.name')" required>
        <NInput
          v-model:value="formData.name"
          :placeholder="t('models.namePlaceholder')"
        />
      </NFormItem>

      <NFormItem :label="t('models.baseUrl')" required>
        <NInput
          v-model:value="formData.baseUrl"
          :placeholder="t('models.baseUrlPlaceholder')"
        />
      </NFormItem>

      <NFormItem :label="t('models.apiKey')">
        <NInput
          v-model:value="formData.apiKey"
          type="password"
          show-password-on="click"
          :placeholder="t('models.apiKeyPlaceholder')"
          autocomplete="off"
        />
      </NFormItem>

      <NFormItem :label="t('models.defaultModel')" required>
        <NInput
          v-model:value="formData.defaultModel"
          :placeholder="t('models.selectOrInput')"
        />
      </NFormItem>

      <NFormItem :label="t('models.dialect')" required>
        <NSelect
          v-model:value="formData.dialect"
          :options="dialectOptions"
        />
      </NFormItem>
    </NForm>

    <template #footer>
      <div class="modal-footer">
        <NButton @click="handleClose">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" :loading="loading" @click="handleSave">
          {{ t('common.add') }}
        </NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped lang="scss">
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
