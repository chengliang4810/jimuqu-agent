<script setup lang="ts">
import { onMounted, ref } from "vue";
import { NButton, NInput, NPopconfirm, useMessage } from "naive-ui";
import { useI18n } from "vue-i18n";
import { clearApiKey, getApiKey, setApiKey } from "@/api/client";
import { fetchRuntimeConfigItems, revealRuntimeConfigItem, setRuntimeConfigItem } from "@/api/hermes/config";

const ACCESS_TOKEN_KEY = "JIMUQU_DASHBOARD_ACCESS_TOKEN";

const { t } = useI18n();
const message = useMessage();

const loading = ref(false);
const saving = ref(false);
const revealing = ref(false);
const configured = ref(false);
const tokenPreview = ref("");
const accessToken = ref("");

onMounted(loadTokenStatus);

async function loadTokenStatus() {
  loading.value = true;
  try {
    const items = await fetchRuntimeConfigItems();
    const item = items[ACCESS_TOKEN_KEY];
    configured.value = !!item?.is_set;
    tokenPreview.value = item?.redacted_value || "";
  } catch (err: any) {
    message.error(err.message || t("common.fetchFailed"));
  } finally {
    loading.value = false;
  }
}

async function saveAccessToken() {
  const nextToken = accessToken.value.trim();
  if (!nextToken) {
    message.error(t("account.accessTokenRequired"));
    return;
  }

  saving.value = true;
  try {
    await setRuntimeConfigItem(ACCESS_TOKEN_KEY, nextToken);
    setApiKey(nextToken);
    accessToken.value = "";
    await loadTokenStatus();
    message.success(t("account.accessTokenSaved"));
    window.location.reload();
  } catch (err: any) {
    message.error(err.message || t("common.saveFailed"));
  } finally {
    saving.value = false;
  }
}

async function revealAccessToken() {
  revealing.value = true;
  try {
    const token = await revealRuntimeConfigItem(ACCESS_TOKEN_KEY);
    accessToken.value = token;
  } catch (err: any) {
    message.error(err.message || t("account.accessTokenRevealFailed"));
  } finally {
    revealing.value = false;
  }
}

async function removeAccessToken() {
  saving.value = true;
  try {
    await setRuntimeConfigItem(ACCESS_TOKEN_KEY, "");
    accessToken.value = "";
    clearApiKey();
    await loadTokenStatus();
    message.success(t("account.accessTokenRemoved"));
    window.location.reload();
  } catch (err: any) {
    message.error(err.message || t("common.saveFailed"));
  } finally {
    saving.value = false;
  }
}

function useCurrentToken() {
  accessToken.value = getApiKey();
}
</script>

<template>
  <div class="account-settings">
    <p class="section-desc">{{ t("account.accessTokenDescription") }}</p>

    <div class="token-card">
      <div class="token-status">
        <span class="status-label">{{ t("account.accessTokenStatus") }}</span>
        <span class="status-value" :class="{ configured }">
          {{ configured ? t("common.configured") : t("common.notConfigured") }}
        </span>
        <code v-if="tokenPreview" class="token-preview">{{ tokenPreview }}</code>
      </div>

      <NInput
        v-model:value="accessToken"
        type="password"
        show-password-on="click"
        :placeholder="t('account.accessTokenPlaceholder')"
        :disabled="loading || saving"
        @keyup.enter="saveAccessToken"
      />

      <div class="action-buttons">
        <NButton @click="useCurrentToken">{{ t("account.useCurrentToken") }}</NButton>
        <NButton :loading="revealing" :disabled="!configured" @click="revealAccessToken">
          {{ t("account.revealAccessToken") }}
        </NButton>
        <NPopconfirm
          :positive-text="t('common.delete')"
          :negative-text="t('common.cancel')"
          @positive-click="removeAccessToken"
        >
          <template #trigger>
            <NButton type="error" ghost :loading="saving" :disabled="!configured">
              {{ t("account.removeAccessToken") }}
            </NButton>
          </template>
          {{ t("account.removeAccessTokenConfirm") }}
        </NPopconfirm>
        <NButton type="primary" :loading="saving" @click="saveAccessToken">
          {{ t("common.save") }}
        </NButton>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use "@/styles/variables" as *;

.account-settings {
  padding: 8px 0;
}

.section-desc {
  font-size: 13px;
  color: $text-muted;
  margin: 0 0 20px;
  line-height: 1.6;
}

.token-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 720px;
}

.token-status {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 13px;
}

.status-label {
  color: $text-secondary;
}

.status-value {
  color: $text-muted;

  &.configured {
    color: $success;
  }
}

.token-preview {
  color: $text-muted;
  background: $bg-input;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  padding: 2px 8px;
}

.action-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
