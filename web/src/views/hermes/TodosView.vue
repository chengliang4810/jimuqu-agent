<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { NButton, NCard, NEmpty, NInput, NModal, NSelect, NSpace, NSpin, NTag, useMessage } from 'naive-ui'
import { createTodo, createTodoWorkspace, getTodoWorkspace, listTodoWorkspaces, updateTodoStatus, type TodoItem, type TodoStatus, type TodoWorkspaceDetail, type TodoWorkspaceSummary } from '@/api/hermes/todos'
import TodoCard from '@/components/hermes/todos/TodoCard.vue'

const message = useMessage()
const loading = ref(false)
const workspaces = ref<TodoWorkspaceSummary[]>([])
const current = ref<TodoWorkspaceDetail | null>(null)
const showTodoWorkspaceModal = ref(false)
const showTodoModal = ref(false)
const workspaceForm = ref({ slug: '', title: '', goal: '' })
const todoForm = ref({ title: '', description: '', priority: 'normal' })
const currentId = computed(() => current.value?.id || current.value?.slug || '')
let pollTimer: number | undefined
const activeWorkspace = computed<TodoWorkspaceDetail>(() => current.value || {
  id: '',
  slug: '',
  title: '',
  goal: '',
  status: '',
  board: [],
  agents: [],
  runs: [],
  questions: [],
  events: [],
})

async function reload(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    workspaces.value = await listTodoWorkspaces()
    if (!current.value && workspaces.value.length) await selectWorkspace(workspaces.value[0].slug)
    else if (current.value) current.value = await getTodoWorkspace(current.value.slug)
  } finally {
    if (showLoading) loading.value = false
  }
}

async function pollCurrent() {
  if (!current.value || loading.value) return
  try {
    await reload(false)
  } catch (_) {
    // 轮询失败不打断用户正在查看的待办面板。
  }
}

async function selectWorkspace(id: string) { current.value = await getTodoWorkspace(id) }
async function submitWorkspace() {
  current.value = await createTodoWorkspace(workspaceForm.value)
  showTodoWorkspaceModal.value = false
  workspaceForm.value = { slug: '', title: '', goal: '' }
  workspaces.value = await listTodoWorkspaces()
}
async function submitTodo() {
  if (!currentId.value) return
  await createTodo(currentId.value, todoForm.value)
  showTodoModal.value = false
  todoForm.value = { title: '', description: '', priority: 'normal' }
  current.value = await getTodoWorkspace(currentId.value)
}
async function handleStatus(todo: TodoItem, status: TodoStatus) {
  if (!currentId.value || todo.status === status) return
  await updateTodoStatus(currentId.value, todo.id, status)
  message.success(`已更新 ${todo.no} -> ${statusLabel(status)}`)
  current.value = await getTodoWorkspace(currentId.value)
}

function statusLabel(status: TodoStatus) {
  const labels: Record<TodoStatus, string> = {
    todo: '待处理',
    in_progress: '进行中',
    waiting_user: '等待用户',
    review: '待复核',
    done: '已完成',
  }
  return labels[status] || status
}

onMounted(() => {
  reload()
  pollTimer = window.setInterval(pollCurrent, 1500)
})

onUnmounted(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})
</script>

<template>
  <div class="todos-view">
    <header class="page-header">
      <div>
        <h1>待办</h1>
        <p>本地待办、分层任务和多 Agent 执行看板。</p>
      </div>
      <NSpace>
        <NButton @click="reload()">刷新</NButton>
        <NButton type="primary" @click="showTodoWorkspaceModal = true">新建待办</NButton>
      </NSpace>
    </header>

    <NSpin :show="loading">
      <div class="workspace">
        <aside class="todo-list">
          <NEmpty v-if="!workspaces.length" description="暂无待办" />
          <button v-for="workspace in workspaces" :key="workspace.id" class="todo-list-item" :class="{ active: current?.id === workspace.id }" @click="selectWorkspace(workspace.slug)">
            <strong>{{ workspace.title }}</strong>
            <span>{{ workspace.slug }}</span>
          </button>
        </aside>

        <main class="board-panel">
          <NEmpty v-if="!current" description="请选择或新建一个待办" />
          <template v-if="current">
            <section class="todo-hero">
              <div>
                <div class="todo-meta">
                  <span class="slug">{{ activeWorkspace.slug }}</span>
                  <NTag v-if="activeWorkspace.autopilot_running" size="small" type="info" round>自动推进中</NTag>
                </div>
                <h2>{{ activeWorkspace.title }}</h2>
                <p>{{ activeWorkspace.goal || '暂未设置目标' }}</p>
                <code>{{ activeWorkspace.dir }}</code>
              </div>
              <NButton type="primary" @click="showTodoModal = true">新增待办</NButton>
            </section>

            <section class="board-grid">
              <NCard v-for="column in activeWorkspace.board" :key="column.status" class="board-column" content-style="padding: 12px">
                <template #header>
                  <div class="column-header"><span>{{ statusLabel(column.status) }}</span><em>{{ column.count }}</em></div>
                </template>
                <div class="todo-stack">
                  <TodoCard v-for="todo in column.todos" :key="todo.id" :todo="todo" @status="handleStatus" />
                  <NEmpty v-if="!column.todos.length" size="small" description="暂无内容" />
                </div>
              </NCard>
            </section>
          </template>
        </main>
      </div>
    </NSpin>

    <NModal
      v-model:show="showTodoWorkspaceModal"
      preset="card"
      title="新建待办"
      :style="{ width: 'min(520px, calc(100vw - 32px))' }"
    >
      <NSpace vertical>
        <NInput v-model:value="workspaceForm.slug" placeholder="待办标识，可选" />
        <NInput v-model:value="workspaceForm.title" placeholder="待办标题" />
        <NInput v-model:value="workspaceForm.goal" type="textarea" placeholder="待办背景 / 目标" />
        <NButton type="primary" block @click="submitWorkspace">创建</NButton>
      </NSpace>
    </NModal>

    <NModal
      v-model:show="showTodoModal"
      preset="card"
      title="新增待办"
      :style="{ width: 'min(520px, calc(100vw - 32px))' }"
    >
      <NSpace vertical>
        <NInput v-model:value="todoForm.title" placeholder="待办标题" />
        <NInput v-model:value="todoForm.description" type="textarea" placeholder="描述" />
        <NSelect v-model:value="todoForm.priority" :options="[{label:'低',value:'low'},{label:'普通',value:'normal'},{label:'高',value:'high'}]" />
        <NButton type="primary" block @click="submitTodo">添加</NButton>
      </NSpace>
    </NModal>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
.todos-view { display: flex; flex-direction: column; gap: 20px; height: 100%; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.page-header h1 { font-size: 28px; color: $text-primary; margin-bottom: 6px; }
.page-header p, .todo-hero p { color: $text-muted; }
.workspace { display: grid; grid-template-columns: 260px 1fr; gap: 18px; min-height: 0; }
.todo-list, .board-panel { border: 1px solid $border-color; border-radius: $radius-lg; background: $bg-card; padding: 14px; }
.todo-list { display: flex; flex-direction: column; gap: 10px; }
.todo-list-item { text-align: left; border: 1px solid $border-color; background: transparent; border-radius: $radius-md; padding: 12px; cursor: pointer; color: $text-primary; }
.todo-list-item span { display: block; margin-top: 4px; color: $text-muted; font-size: 12px; }
.todo-list-item.active, .todo-list-item:hover { border-color: rgba(var(--accent-primary-rgb), .35); background: rgba(var(--accent-primary-rgb), .07); }
.board-panel { min-width: 0; }
.todo-hero { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.todo-hero h2 { font-size: 22px; color: $text-primary; margin: 4px 0 8px; }
.todo-hero code { display: inline-block; margin-top: 8px; color: $text-muted; font-size: 12px; }
.todo-meta { display: flex; align-items: center; gap: 8px; }
.slug { color: $accent-primary; font-size: 12px; font-weight: 700; }
.board-grid { display: grid; grid-template-columns: repeat(5, minmax(220px, 1fr)); gap: 12px; overflow-x: auto; padding-bottom: 4px; }
.board-column { min-width: 220px; background: rgba(var(--bg-card), .68); }
.column-header { display: flex; justify-content: space-between; align-items: center; font-size: 13px; font-weight: 700; color: $text-secondary; }
.column-header em { font-style: normal; color: $accent-primary; }
.todo-stack { display: flex; flex-direction: column; gap: 10px; }
</style>


