import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { setManagementProfile as setApiManagementProfile } from '@/api/client'
import * as profilesApi from '@/api/solonclaw/profiles'
import type {
  CreateProfileRequest,
  CreateProfileResult,
  InstallProfileDistributionRequest,
  ProfileDescribeAutoResult,
  SolonClawProfile,
} from '@/api/solonclaw/profiles'
import { normalizeManagementProfile } from '@/shared/profileScope'
import { updateProfileContext } from '@/shared/profileContext'

export const useProfilesStore = defineStore('profiles', () => {
  const profiles = ref<SolonClawProfile[]>([])
  const managementProfile = ref('')
  const loading = ref(false)
  const mutating = ref(false)
  const initialized = ref(false)
  const loadError = ref<string | null>(null)

  const managedProfileName = computed(() => managementProfile.value || 'default')

  /** 更新由 Profile 卡片深链选中的管理目标；空值与 default 均使用默认上下文。 */
  function setManagementProfile(name: string): void {
    const normalized = normalizeManagementProfile(name)
    managementProfile.value = normalized
    setApiManagementProfile(normalized)
    updateProfileContext(normalized || 'default')
  }

  /** 加载专业执行单元列表，并清理已经不存在的深链目标。 */
  async function fetchProfiles(): Promise<void> {
    loading.value = true
    loadError.value = null
    try {
      const response = await profilesApi.fetchProfiles()
      profiles.value = response.profiles || []

      const known = new Set(profiles.value.map(profile => profile.name))
      if (managementProfile.value && !known.has(managementProfile.value)) {
        setManagementProfile('')
      } else {
        setManagementProfile(managementProfile.value)
      }
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : String(error || 'Failed to load profiles')
      throw error
    } finally {
      loading.value = false
      initialized.value = true
    }
  }

  /** 首次进入 Dashboard 时加载 Profile 列表。 */
  async function initialize(): Promise<void> {
    if (initialized.value || loading.value) return
    await fetchProfiles()
  }

  async function mutate<T>(action: () => Promise<T>): Promise<T> {
    mutating.value = true
    try {
      return await action()
    } finally {
      mutating.value = false
    }
  }

  async function createProfile(body: CreateProfileRequest): Promise<CreateProfileResult> {
    return mutate(async () => {
      const profile = await profilesApi.createProfile(body)
      await fetchProfiles()
      return profile
    })
  }

  async function updateDescription(name: string, description: string): Promise<void> {
    await mutate(async () => {
      await profilesApi.updateProfileDescription(name, description)
      await fetchProfiles()
    })
  }

  async function describeAutomatically(name: string, overwrite = true): Promise<ProfileDescribeAutoResult> {
    return mutate(async () => {
      const result = await profilesApi.describeProfileAutomatically(name, overwrite)
      await fetchProfiles()
      return result
    })
  }

  async function updateSoul(name: string, content: string): Promise<void> {
    await mutate(() => profilesApi.updateProfileSoul(name, content))
  }

  async function updateModel(name: string, provider: string, model: string): Promise<void> {
    await mutate(async () => {
      await profilesApi.updateProfileModel(name, provider, model)
      await fetchProfiles()
    })
  }

  async function createAlias(name: string, alias?: string): Promise<void> {
    await mutate(async () => {
      await profilesApi.createProfileAlias(name, alias)
      await fetchProfiles()
    })
  }

  async function removeAlias(name: string, alias?: string): Promise<void> {
    await mutate(async () => {
      await profilesApi.removeProfileAlias(name, alias)
      await fetchProfiles()
    })
  }

  async function installDistribution(body: InstallProfileDistributionRequest): Promise<void> {
    await mutate(async () => {
      await profilesApi.installProfileDistribution(body)
      await fetchProfiles()
    })
  }

  async function updateDistribution(name: string, forceConfig = false): Promise<void> {
    await mutate(async () => {
      await profilesApi.updateProfileDistribution(name, forceConfig)
      await fetchProfiles()
    })
  }

  async function renameProfile(name: string, newName: string): Promise<void> {
    await mutate(async () => {
      await profilesApi.renameProfile(name, newName)
      if (managementProfile.value === name) setManagementProfile(newName)
      await fetchProfiles()
    })
  }

  async function deleteProfile(name: string): Promise<void> {
    await mutate(async () => {
      await profilesApi.deleteProfile(name)
      if (managementProfile.value === name) setManagementProfile('')
      await fetchProfiles()
    })
  }

  async function importProfile(file: File, name?: string): Promise<void> {
    await mutate(async () => {
      await profilesApi.importProfile(file, name)
      await fetchProfiles()
    })
  }

  return {
    profiles,
    managementProfile,
    managedProfileName,
    loading,
    mutating,
    initialized,
    loadError,
    setManagementProfile,
    fetchProfiles,
    initialize,
    createProfile,
    updateDescription,
    describeAutomatically,
    fetchSoul: profilesApi.fetchProfileSoul,
    updateSoul,
    updateModel,
    createAlias,
    removeAlias,
    installDistribution,
    updateDistribution,
    renameProfile,
    deleteProfile,
    importProfile,
    exportProfile: profilesApi.exportProfile,
  }
})
