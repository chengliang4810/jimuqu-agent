<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Button, Form, FormItem, Input, InputNumber, Modal, Select, Spin, Tag, TextArea, message } from 'antdv-next'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { createProfileTask, fetchProfileTasks, type ProfileTask } from '@/api/solonclaw/profileTasks'
import type { SolonClawProfile } from '@/api/solonclaw/profiles'
import { useProfilesStore } from '@/stores/solonclaw/profiles'

type Activity = 'idle' | 'working' | 'waiting' | 'blocked'

const { t } = useI18n()
const router = useRouter()
const profilesStore = useProfilesStore()
const search = ref('')
const statusFilter = ref('all')
const assignOpen = ref(false)
const tasksOpen = ref(false)
const selectedProfile = ref<SolonClawProfile | null>(null)
const taskDescription = ref('')
const timeoutMinutes = ref(30)
const taskSubmitting = ref(false)
const tasksLoading = ref(false)
const tasks = ref<ProfileTask[]>([])
const tasksByProfile = ref<Record<string, ProfileTask[]>>({})
/** Profile 活动状态的刷新周期，避免任务状态长期停留在首次加载结果。 */
const activityRefreshIntervalMs = 5000
/** Profile 活动状态的定时刷新句柄。 */
const activityRefreshTimer = ref<ReturnType<typeof setInterval> | null>(null)
/** 防止慢请求期间重复刷新同一批 ProfileTask。 */
const tasksRefreshInFlight = ref(false)
const surfaceRoutes = computed(() => [
  { label: t('profileAgents.viewSkills'), name: 'solonclaw.skills' },
  { label: t('profileAgents.viewMemory'), name: 'solonclaw.persona.journal' },
  { label: t('profileAgents.viewSessions'), name: 'solonclaw.runs' },
  { label: t('profileAgents.viewChannels'), name: 'solonclaw.channels' },
])

const statusOptions = computed(() => [
  { label: t('profileAgents.allStatuses'), value: 'all' },
  { label: t('profileAgents.working'), value: 'working' },
  { label: t('profileAgents.waiting'), value: 'waiting' },
  { label: t('profileAgents.blocked'), value: 'blocked' },
  { label: t('profileAgents.idle'), value: 'idle' },
])

const agents = computed(() => {
  const query = search.value.trim().toLowerCase()
  return profilesStore.profiles.filter(profile => {
    const matchesText = !query || `${profile.name} ${profile.description}`.toLowerCase().includes(query)
    const matchesStatus = statusFilter.value === 'all' || activity(profile) === statusFilter.value
    return matchesText && matchesStatus
  })
})

onMounted(async () => {
  try {
    await profilesStore.fetchProfiles()
    await loadAgentTasks()
  } catch {
    // Profile store already exposes its loading error; task activity can degrade to idle.
  } finally {
    activityRefreshTimer.value = setInterval(() => {
      void loadAgentTasks()
    }, activityRefreshIntervalMs)
  }
})

onUnmounted(() => {
  if (activityRefreshTimer.value) {
    clearInterval(activityRefreshTimer.value)
  }
})

/** 读取各专业执行单元的任务状态，并原子替换活动状态快照。 */
async function loadAgentTasks(): Promise<void> {
  if (tasksRefreshInFlight.value) return
  tasksRefreshInFlight.value = true
  try {
    const entries = await Promise.all(profilesStore.profiles.map(async profile => {
      try {
        return [profile.name, await fetchProfileTasks(profile.name)] as const
      } catch {
        return [profile.name, []] as const
      }
    }))
    tasksByProfile.value = Object.fromEntries(entries)
  } finally {
    tasksRefreshInFlight.value = false
  }
}

function activity(profile: SolonClawProfile): Activity {
  const profileTasks = tasksByProfile.value[profile.name] || []
  if (profileTasks.some(task => task.status === 'RUNNING')) return 'working'
  if (profileTasks.some(task => task.status === 'PENDING' || task.status === 'READY')) return 'waiting'
  if (profileTasks.some(task => task.status === 'BLOCKED')) return 'blocked'
  return 'idle'
}

function activeTask(profile: SolonClawProfile): ProfileTask | undefined {
  return (tasksByProfile.value[profile.name] || []).find(task => task.status === 'RUNNING')
}

function taskCount(profile: SolonClawProfile, statuses: ProfileTask['status'][]): number {
  return (tasksByProfile.value[profile.name] || []).filter(task => statuses.includes(task.status)).length
}

function initials(name: string): string {
  return name.slice(0, 2).toUpperCase()
}

function openAssign(profile: SolonClawProfile): void {
  selectedProfile.value = profile
  taskDescription.value = ''
  timeoutMinutes.value = 30
  assignOpen.value = true
}

async function submitTask(): Promise<void> {
  const profile = selectedProfile.value
  const description = taskDescription.value.trim()
  if (!profile || !description) return
  taskSubmitting.value = true
  try {
    await createProfileTask({
      source_profile: 'default',
      target_profile: profile.name,
      description,
      depends_on: [],
      timeout_minutes: timeoutMinutes.value,
    })
    assignOpen.value = false
    message.success(t('profileAgents.taskCreated'))
    await profilesStore.fetchProfiles()
    await loadAgentTasks()
  } catch (error) {
    message.error(error instanceof Error ? error.message : t('profileAgents.taskCreateFailed'))
  } finally {
    taskSubmitting.value = false
  }
}

async function openTasks(profile: SolonClawProfile): Promise<void> {
  selectedProfile.value = profile
  tasks.value = []
  tasksOpen.value = true
  tasksLoading.value = true
  try {
    tasks.value = await fetchProfileTasks(profile.name)
  } catch (error) {
    message.error(error instanceof Error ? error.message : t('profileAgents.taskLoadFailed'))
  } finally {
    tasksLoading.value = false
  }
}

function openSurface(profile: SolonClawProfile, routeName: string): void {
  profilesStore.setManagementProfile(profile.name)
  void router.push({ name: routeName, query: { profile: profile.name } })
}

function taskTagColor(status: ProfileTask['status']): string {
  if (status === 'COMPLETED') return 'success'
  if (status === 'RUNNING') return 'processing'
  if (status === 'FAILED' || status === 'TIMED_OUT' || status === 'INTERRUPTED') return 'error'
  if (status === 'BLOCKED') return 'warning'
  return 'default'
}
</script>

<template>
  <div class="agents-view">
    <header class="page-header">
      <div>
        <h1>{{ t('profileAgents.title') }}</h1>
        <p>{{ t('profileAgents.subtitle') }}</p>
      </div>
      <Button type="primary" @click="router.push({ name: 'solonclaw.profiles.new' })">
        {{ t('profileAgents.create') }}
      </Button>
    </header>

    <main class="agents-content">
      <div class="toolbar">
        <Input v-model:value="search" allow-clear :placeholder="t('profileAgents.search')" />
        <Select v-model:value="statusFilter" :options="statusOptions" />
      </div>

      <Spin :spinning="profilesStore.loading">
        <div v-if="agents.length" class="agent-grid">
          <article v-for="profile in agents" :key="profile.name" class="agent-card">
            <header class="card-header">
              <div class="agent-identity">
                <div class="avatar" aria-hidden="true">{{ initials(profile.name) }}</div>
                <div>
                  <div class="name-row">
                    <h2>{{ profile.name }}</h2>
                    <Tag v-if="profile.name === 'default'">{{ t('profileAgents.defaultBadge') }}</Tag>
                  </div>
                  <p>{{ profile.description || (profile.name === 'default' ? t('profileAgents.defaultDescription') : t('profiles.noDescription')) }}</p>
                </div>
              </div>
            </header>

            <div class="status-row">
              <div>
                <span class="field-label">{{ t('profileAgents.activity') }}</span>
                <span class="status" :class="activity(profile)"><i />{{ t(`profileAgents.${activity(profile)}`) }}</span>
              </div>
            </div>

            <div class="agent-meta">
              <div><span>{{ t('profileAgents.model') }}</span><strong>{{ profile.model || '-' }}</strong></div>
              <div><span>{{ t('profileAgents.skills') }}</span><strong>{{ profile.skills_count }}</strong></div>
            </div>

            <div v-if="activeTask(profile)" class="current-task">
              <span>{{ t('profileAgents.currentTask') }}</span>
              <strong>{{ activeTask(profile)?.prompt }}</strong>
            </div>
            <p v-if="taskCount(profile, ['RUNNING']) || taskCount(profile, ['PENDING', 'READY'])" class="task-summary">
              {{ t('profileAgents.taskSummary', { running: taskCount(profile, ['RUNNING']), waiting: taskCount(profile, ['PENDING', 'READY']) }) }}
            </p>

            <footer>
              <Button type="primary" :disabled="profile.name === 'default'" @click="openAssign(profile)">{{ t('profileAgents.assignTask') }}</Button>
              <Button @click="openTasks(profile)">{{ t('profileAgents.viewTasks') }}</Button>
              <Button
                v-for="surface in surfaceRoutes"
                :key="surface.name"
                @click="openSurface(profile, surface.name)"
              >
                {{ surface.label }}
              </Button>
            </footer>
          </article>
        </div>
        <div v-else class="empty-state">{{ t('profileAgents.empty') }}</div>
      </Spin>
    </main>

    <Modal v-model:open="assignOpen" :title="t('profileAgents.assignTitle', { name: selectedProfile?.name || '' })">
      <Form layout="vertical">
        <FormItem :label="t('profileAgents.taskDescription')" required>
          <TextArea v-model:value="taskDescription" :autosize="{ minRows: 5, maxRows: 12 }" :placeholder="t('profileAgents.taskPlaceholder')" />
        </FormItem>
        <FormItem :label="t('profileAgents.timeout')">
          <InputNumber v-model:value="timeoutMinutes" :min="1" :max="240" />
        </FormItem>
      </Form>
      <template #footer>
        <Button @click="assignOpen = false">{{ t('common.cancel') }}</Button>
        <Button type="primary" :loading="taskSubmitting" :disabled="!taskDescription.trim()" @click="submitTask">{{ t('profileAgents.assignTask') }}</Button>
      </template>
    </Modal>

    <Modal v-model:open="tasksOpen" :title="t('profileAgents.tasksTitle', { name: selectedProfile?.name || '' })" :footer="null">
      <Spin :spinning="tasksLoading">
        <div v-if="tasks.length" class="task-list">
          <article v-for="task in tasks" :key="task.taskId" class="task-item">
            <div class="task-heading">
              <Tag :color="taskTagColor(task.status)">{{ task.status }}</Tag>
              <span>{{ t('profileAgents.attempts', { current: task.attemptCount, max: task.maxAttempts }) }}</span>
            </div>
            <p>{{ task.prompt }}</p>
            <small v-if="task.error">{{ task.error }}</small>
          </article>
        </div>
        <div v-else class="empty-state compact">{{ t('profileAgents.noTasks') }}</div>
      </Spin>
    </Modal>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.agents-view { min-height: 100%; color: $text-primary; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 20px 24px; border-bottom: 1px solid $border-color; }
.page-header h1 { margin: 0; font-size: 20px; }
.page-header p { margin: 4px 0 0; color: $text-secondary; font-size: 13px; }
.agents-content { padding: 18px 24px 28px; }
.toolbar { display: grid; grid-template-columns: minmax(220px, 420px) 180px; gap: 10px; margin-bottom: 16px; }
.agent-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 340px), 1fr)); gap: 14px; }
.agent-card { display: flex; min-width: 0; flex-direction: column; gap: 14px; padding: 16px; border: 1px solid $border-color; border-radius: 8px; background: $bg-primary; }
.card-header, .agent-identity, .name-row, .status-row, .agent-meta, .agent-card footer, .task-heading { display: flex; align-items: center; }
.card-header { justify-content: space-between; align-items: flex-start; gap: 12px; }
.agent-identity { min-width: 0; align-items: flex-start; gap: 12px; }
.avatar { display: grid; width: 38px; height: 38px; flex: 0 0 38px; place-items: center; border-radius: 8px; color: $text-on-accent; background: $accent-primary; font-size: 12px; font-weight: 700; }
.name-row { flex-wrap: wrap; gap: 6px; }
.name-row h2 { margin: 0; font-size: 16px; overflow-wrap: anywhere; }
.agent-identity p { display: -webkit-box; margin: 4px 0 0; overflow: hidden; color: $text-secondary; font-size: 12px; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.status-row { gap: 24px; padding: 11px 0; border-block: 1px solid $border-color; }
.status-row > div { display: grid; gap: 4px; }
.field-label, .agent-meta span, .current-task span { color: $text-muted; font-size: 11px; }
.status { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 600; }
.status i { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.status.idle { color: $success; }
.status.working { color: $accent-primary; }
.status.waiting, .status.blocked { color: $warning; }
.agent-meta { gap: 18px; }
.agent-meta div { display: grid; min-width: 0; gap: 3px; }
.agent-meta div:first-child { flex: 1; }
.agent-meta strong { overflow: hidden; font-size: 12px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.current-task { display: grid; gap: 4px; padding: 10px; border-radius: 6px; background: $bg-secondary; }
.current-task strong { font-size: 12px; font-weight: 600; }
.task-summary { margin: -6px 0 0; color: $text-secondary; font-size: 12px; }
.agent-card footer { flex-wrap: wrap; gap: 8px; margin-top: auto; }
.empty-state { padding: 32px; border: 1px dashed $border-color; border-radius: 8px; color: $text-secondary; text-align: center; }
.empty-state.compact { padding: 18px; }
.task-list { display: grid; max-height: min(60vh, 560px); gap: 10px; overflow: auto; }
.task-item { padding: 12px; border: 1px solid $border-color; border-radius: 8px; }
.task-heading { justify-content: space-between; gap: 8px; color: $text-muted; font-size: 11px; }
.task-item p { margin: 10px 0 0; color: $text-primary; white-space: pre-wrap; }
.task-item small { display: block; margin-top: 8px; color: $error; }

@media (max-width: 640px) {
  .page-header { align-items: flex-start; padding: 16px; }
  .agents-content { padding: 14px 16px 22px; }
  .toolbar { grid-template-columns: 1fr; }
  .status-row { gap: 16px; }
}
</style>
