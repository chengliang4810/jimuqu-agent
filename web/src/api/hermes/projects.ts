import { request } from '../client'

export type ProjectTodoStatus = 'todo' | 'in_progress' | 'waiting_user' | 'review' | 'done'

export interface ProjectTodo {
  id: string
  project_id: string
  parent_todo_id?: string | null
  no: string
  title: string
  description?: string
  status: ProjectTodoStatus
  assigned_agent?: string
  priority?: string
  child_total: number
  child_done: number
  updated_at?: string
}

export interface ProjectColumn {
  status: ProjectTodoStatus
  title: string
  count: number
  todos: ProjectTodo[]
}

export interface ProjectSummary {
  id: string
  slug: string
  title: string
  goal?: string
  status: string
  current_todo_id?: string | null
  updated_at?: string
  created_at?: string
  dir?: string
  counts?: Record<ProjectTodoStatus, number>
}

export interface ProjectDetail extends ProjectSummary {
  board: ProjectColumn[]
  agents: Array<{ agent_name: string; role_hint?: string }>
  runs: Array<{ id: string; todo_id?: string; agent_name?: string; status: string; summary?: string; started_at?: string }>
  questions: Array<{ id: string; todo_id?: string; asked_by?: string; question: string; answer?: string; status: string; created_at?: string }>
  events: Array<{ id: string; todo_id?: string; type: string; actor?: string; message?: string; created_at?: string }>
}

export async function listProjects(): Promise<ProjectSummary[]> {
  const data = await request<{ projects: ProjectSummary[] }>('/api/projects')
  return data.projects || []
}

export async function createProject(data: { slug?: string; title: string; goal?: string }): Promise<ProjectDetail> {
  return request<ProjectDetail>('/api/projects', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getProject(id: string): Promise<ProjectDetail> {
  return request<ProjectDetail>(`/api/projects/${encodeURIComponent(id)}`)
}

export async function createTodo(projectId: string, data: { title: string; description?: string; parent_todo_id?: string; priority?: string }): Promise<ProjectTodo> {
  return request<ProjectTodo>(`/api/projects/${encodeURIComponent(projectId)}/todos`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function updateTodoStatus(projectId: string, todoId: string, status: ProjectTodoStatus): Promise<ProjectTodo> {
  return request<ProjectTodo>(`/api/projects/${encodeURIComponent(projectId)}/todos/${encodeURIComponent(todoId)}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}
