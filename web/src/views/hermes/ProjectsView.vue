<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NCard, NEmpty, NInput, NModal, NSelect, NSpace, NSpin, useMessage } from 'naive-ui'
import { createProject, createTodo, getProject, listProjects, updateTodoStatus, type ProjectDetail, type ProjectSummary, type ProjectTodo, type ProjectTodoStatus } from '@/api/hermes/projects'
import ProjectTodoCard from '@/components/hermes/projects/ProjectTodoCard.vue'

const message = useMessage()
const loading = ref(false)
const projects = ref<ProjectSummary[]>([])
const current = ref<ProjectDetail | null>(null)
const showProjectModal = ref(false)
const showTodoModal = ref(false)
const projectForm = ref({ slug: '', title: '', goal: '' })
const todoForm = ref({ title: '', description: '', priority: 'normal' })
const currentId = computed(() => current.value?.id || current.value?.slug || '')
const activeProject = computed<ProjectDetail>(() => current.value || {
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

async function reload() {
  loading.value = true
  try {
    projects.value = await listProjects()
    if (!current.value && projects.value.length) await selectProject(projects.value[0].slug)
    else if (current.value) current.value = await getProject(current.value.slug)
  } finally {
    loading.value = false
  }
}

async function selectProject(id: string) { current.value = await getProject(id) }
async function submitProject() {
  current.value = await createProject(projectForm.value)
  showProjectModal.value = false
  projectForm.value = { slug: '', title: '', goal: '' }
  projects.value = await listProjects()
}
async function submitTodo() {
  if (!currentId.value) return
  await createTodo(currentId.value, todoForm.value)
  showTodoModal.value = false
  todoForm.value = { title: '', description: '', priority: 'normal' }
  current.value = await getProject(currentId.value)
}
async function handleStatus(todo: ProjectTodo, status: ProjectTodoStatus) {
  if (!currentId.value || todo.status === status) return
  await updateTodoStatus(currentId.value, todo.id, status)
  message.success(`已更新 ${todo.no} -> ${statusLabel(status)}`)
  current.value = await getProject(currentId.value)
}

function statusLabel(status: ProjectTodoStatus) {
  const labels: Record<ProjectTodoStatus, string> = {
    todo: '待处理',
    in_progress: '进行中',
    waiting_user: '等待用户',
    review: '待复核',
    done: '已完成',
  }
  return labels[status] || status
}

onMounted(reload)
</script>

<template>
  <div class="projects-view">
    <header class="page-header">
      <div>
        <h1>项目</h1>
        <p>本地项目工作台、分层待办和多 Agent 看板。</p>
      </div>
      <NSpace>
        <NButton @click="reload">刷新</NButton>
        <NButton type="primary" @click="showProjectModal = true">新建项目</NButton>
      </NSpace>
    </header>

    <NSpin :show="loading">
      <div class="workspace">
        <aside class="project-list">
          <NEmpty v-if="!projects.length" description="暂无项目" />
          <button v-for="project in projects" :key="project.id" class="project-item" :class="{ active: current?.id === project.id }" @click="selectProject(project.slug)">
            <strong>{{ project.title }}</strong>
            <span>{{ project.slug }}</span>
          </button>
        </aside>

        <main class="board-panel">
          <NEmpty v-if="!current" description="请选择或新建一个项目" />
          <template v-if="current">
            <section class="project-hero">
              <div>
                <div class="slug">{{ activeProject.slug }}</div>
                <h2>{{ activeProject.title }}</h2>
                <p>{{ activeProject.goal || '暂未设置目标' }}</p>
                <code>{{ activeProject.dir }}</code>
              </div>
              <NButton type="primary" @click="showTodoModal = true">新增待办</NButton>
            </section>

            <section class="board-grid">
              <NCard v-for="column in activeProject.board" :key="column.status" class="board-column" content-style="padding: 12px">
                <template #header>
                  <div class="column-header"><span>{{ statusLabel(column.status) }}</span><em>{{ column.count }}</em></div>
                </template>
                <div class="todo-stack">
                  <ProjectTodoCard v-for="todo in column.todos" :key="todo.id" :todo="todo" @status="handleStatus" />
                  <NEmpty v-if="!column.todos.length" size="small" description="暂无内容" />
                </div>
              </NCard>
            </section>
          </template>
        </main>
      </div>
    </NSpin>

    <NModal v-model:show="showProjectModal" preset="card" title="新建项目" class="project-modal">
      <NSpace vertical>
        <NInput v-model:value="projectForm.slug" placeholder="项目标识，可选" />
        <NInput v-model:value="projectForm.title" placeholder="项目标题" />
        <NInput v-model:value="projectForm.goal" type="textarea" placeholder="项目背景 / 目标" />
        <NButton type="primary" block @click="submitProject">创建</NButton>
      </NSpace>
    </NModal>

    <NModal v-model:show="showTodoModal" preset="card" title="新增待办" class="project-modal">
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
.projects-view { display: flex; flex-direction: column; gap: 20px; height: 100%; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.page-header h1 { font-size: 28px; color: $text-primary; margin-bottom: 6px; }
.page-header p, .project-hero p { color: $text-muted; }
.workspace { display: grid; grid-template-columns: 260px 1fr; gap: 18px; min-height: 0; }
.project-list, .board-panel { border: 1px solid $border-color; border-radius: $radius-lg; background: $bg-card; padding: 14px; }
.project-list { display: flex; flex-direction: column; gap: 10px; }
.project-item { text-align: left; border: 1px solid $border-color; background: transparent; border-radius: $radius-md; padding: 12px; cursor: pointer; color: $text-primary; }
.project-item span { display: block; margin-top: 4px; color: $text-muted; font-size: 12px; }
.project-item.active, .project-item:hover { border-color: rgba(var(--accent-primary-rgb), .35); background: rgba(var(--accent-primary-rgb), .07); }
.board-panel { min-width: 0; }
.project-hero { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.project-hero h2 { font-size: 22px; color: $text-primary; margin: 4px 0 8px; }
.project-hero code { display: inline-block; margin-top: 8px; color: $text-muted; font-size: 12px; }
.slug { color: $accent-primary; font-size: 12px; font-weight: 700; }
.board-grid { display: grid; grid-template-columns: repeat(5, minmax(220px, 1fr)); gap: 12px; overflow-x: auto; padding-bottom: 4px; }
.board-column { min-width: 220px; background: rgba(var(--bg-card), .68); }
.column-header { display: flex; justify-content: space-between; align-items: center; font-size: 13px; font-weight: 700; color: $text-secondary; }
.column-header em { font-style: normal; color: $accent-primary; }
.todo-stack { display: flex; flex-direction: column; gap: 10px; }
.project-modal { width: 520px; }
</style>


