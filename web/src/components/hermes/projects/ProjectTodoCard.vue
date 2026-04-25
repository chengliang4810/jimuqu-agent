<script setup lang="ts">
import { computed } from 'vue'
import { NButton, NSelect } from 'naive-ui'
import type { ProjectTodo, ProjectTodoStatus } from '@/api/hermes/projects'

const props = defineProps<{ todo: ProjectTodo }>()
const emit = defineEmits<{ status: [todo: ProjectTodo, status: ProjectTodoStatus] }>()

const statusOptions = [
  { label: '待处理', value: 'todo' },
  { label: '进行中', value: 'in_progress' },
  { label: '等待用户', value: 'waiting_user' },
  { label: '待复核', value: 'review' },
  { label: '已完成', value: 'done' },
]

const priorityLabels: Record<string, string> = {
  low: '低',
  normal: '普通',
  high: '高',
  urgent: '紧急',
}

const updated = computed(() => props.todo.updated_at ? new Date(props.todo.updated_at).toLocaleString() : '-')
const progress = computed(() => props.todo.child_total > 0 ? `${props.todo.child_done}/${props.todo.child_total}` : '')
const priorityLabel = computed(() => priorityLabels[props.todo.priority || 'normal'] || props.todo.priority || '普通')
const agentLabel = computed(() => props.todo.assigned_agent === 'project-manager' || !props.todo.assigned_agent ? '项目经理' : props.todo.assigned_agent)
function handleStatus(value: string) { emit('status', props.todo, value as ProjectTodoStatus) }
</script>

<template>
  <article class="project-todo-card">
    <div class="card-topline">
      <span class="todo-no">{{ todo.no }}</span>
      <span v-if="progress" class="progress-chip">子待办 {{ progress }}</span>
      <span class="agent-chip">{{ agentLabel }}</span>
    </div>
    <h3 class="todo-title">{{ todo.title }}</h3>
    <p v-if="todo.description" class="todo-description">{{ todo.description }}</p>
    <div class="card-footer">
      <span class="priority" :class="todo.priority || 'normal'">{{ priorityLabel }}</span>
      <span class="updated">{{ updated }}</span>
    </div>
    <div class="card-actions">
      <NSelect size="tiny" :value="todo.status" :options="statusOptions" @update:value="handleStatus" />
      <NButton v-if="todo.status !== 'done'" size="tiny" quaternary type="primary" @click="handleStatus('done')">完成</NButton>
    </div>
  </article>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
.project-todo-card { border: 1px solid $border-color; border-radius: $radius-md; background: $bg-card; padding: 14px; display: flex; flex-direction: column; gap: 10px; transition: border-color $transition-fast, transform $transition-fast; }
.project-todo-card:hover { border-color: rgba(var(--accent-primary-rgb), 0.32); transform: translateY(-1px); }
.card-topline, .card-footer, .card-actions { display: flex; align-items: center; gap: 8px; }
.card-topline { flex-wrap: wrap; }
.todo-no { color: $accent-primary; font-size: 12px; font-weight: 700; font-family: \$font-code; }
.progress-chip, .agent-chip, .priority { font-size: 11px; line-height: 1; padding: 4px 8px; border-radius: 999px; background: rgba(var(--accent-primary-rgb), 0.08); color: $text-secondary; }
.agent-chip { background: rgba(var(--accent-info-rgb), 0.10); }
.todo-title { color: $text-primary; font-size: 15px; font-weight: 600; line-height: 1.35; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.todo-description { color: $text-muted; font-size: 12px; line-height: 1.55; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.card-footer { justify-content: space-between; color: $text-muted; font-size: 12px; }
.priority.high, .priority.urgent { color: $warning; background: rgba(var(--warning-rgb), 0.12); }
.priority.low { color: $text-muted; }
.updated { white-space: nowrap; }
.card-actions { justify-content: space-between; padding-top: 4px; }
</style>



