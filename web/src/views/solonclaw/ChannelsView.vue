<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Spin } from 'antdv-next'
import { useI18n } from 'vue-i18n'
import { useSettingsStore } from '@/stores/solonclaw/settings'
import { useProfilesStore } from '@/stores/solonclaw/profiles'
import PlatformSettings from '@/components/solonclaw/settings/PlatformSettings.vue'
import PairingControl from '@/components/solonclaw/channels/PairingControl.vue'
import { normalizePlatformSettingsItems } from '@/components/solonclaw/settings/platformDefinitions'

const settingsStore = useSettingsStore()
const profilesStore = useProfilesStore()
const { t } = useI18n()
const platformLabels = computed(() => Object.fromEntries(
  normalizePlatformSettingsItems(settingsStore.platformCatalog, key => t(key)).map(item => [item.key, item.name]),
))

onMounted(() => {
  settingsStore.fetchSettings()
  void profilesStore.initialize().catch(() => {})
})
</script>

<template>
  <div class="channels-view">
    <header class="page-header">
      <div>
        <h2 class="header-title">{{ t('sidebar.channels') }}</h2>
        <p class="header-subtitle">{{ t('channels.description') }}</p>
      </div>
    </header>

    <div class="channels-content">
      <Spin :spinning="settingsStore.loading || settingsStore.saving" size="large" :description="t('common.loading')">
        <PlatformSettings v-if="!settingsStore.loading" />
      </Spin>

      <PairingControl
        :profile-name="profilesStore.managedProfileName"
        :platform-settings="settingsStore.platforms"
        :platform-labels="platformLabels"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.channels-view {
  height: calc(100 * var(--vh));
  display: flex;
  flex-direction: column;
}

.channels-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  position: relative;
}
</style>
