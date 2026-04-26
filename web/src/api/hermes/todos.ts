import { request } from '../client'

export type TodoStatus = 'todo' | 'in_progress' | 'waiting_user' | 'review' | 'done'

export interface TodoItem {
  id: string
  workspace_id: string
  parent_todo_id?: string | null
  no: string
  title: string
  description?: string
  status: TodoStatus
  assigned_agent?: string
  priority?: string
  child_total: number
  child_done: number
  updated_at?: string
}

export interface TodoColumn {
  status: TodoStatus
  title: string
  count: number
  todos: TodoItem[]
}

export interface TodoWorkspaceSummary {
  id: string
  slug: string
  title: string
  goal?: string
  status: string
  current_todo_id?: string | null
  updated_at?: string
  created_at?: string
  dir?: string
  autopilot_running?: boolean
  counts?: Record<TodoStatus, number>
}

export interface TodoWorkspaceDetail extends TodoWorkspaceSummary {
  board: TodoColumn[]
  agents: Array<{ agent_name: string; role_hint?: string }>
  runs: Array<{ id: string; todo_id?: string; agent_name?: string; status: string; summary?: string; started_at?: string }>
  questions: Array<{ id: string; todo_id?: string; asked_by?: string; question: string; answer?: string; status: string; created_at?: string }>
  events: Array<{ id: string; todo_id?: string; type: string; actor?: string; message?: string; created_at?: string }>
}

export async function listTodoWorkspaces(): Promise<TodoWorkspaceSummary[]> {
  const data = await request<{ todos: TodoWorkspaceSummary[] }>('/api/todos')
  return data.todos || []
}

export async function createTodoWorkspace(data: { slug?: string; title: string; goal?: string }): Promise<TodoWorkspaceDetail> {
  return request<TodoWorkspaceDetail>('/api/todos', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getTodoWorkspace(id: string): Promise<TodoWorkspaceDetail> {
  return request<TodoWorkspaceDetail>(`/api/todos/${encodeURIComponent(id)}`)
}

export async function createTodo(workspaceId: string, data: { title: string; description?: string; parent_todo_id?: string; priority?: string }): Promise<TodoItem> {
  return request<TodoItem>(`/api/todos/${encodeURIComponent(workspaceId)}/items`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function updateTodoStatus(workspaceId: string, todoId: string, status: TodoStatus): Promise<TodoItem> {
  return request<TodoItem>(`/api/todos/${encodeURIComponent(workspaceId)}/items/${encodeURIComponent(todoId)}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}
