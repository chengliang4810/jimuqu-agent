import { request } from '../client'

export interface SkillInfo {
  name: string
  description: string
  enabled?: boolean
}

export interface SkillCategory {
  name: string
  description: string
  skills: SkillInfo[]
}

export interface SkillListResponse {
  categories: SkillCategory[]
}

export interface SkillFileEntry {
  path: string
  name: string
  isDir: boolean
}

export interface MemoryData {
  memory: string
  user: string
  soul: string
  memory_mtime: number | null
  user_mtime: number | null
  soul_mtime: number | null
}

interface DashboardSkill {
  name: string
  description: string
  category: string
  enabled: boolean
}

interface WorkspaceFile {
  key: string
  path: string
  content: string
}

function displayCategory(name: string): string {
  return name || 'general'
}

export async function fetchSkills(): Promise<SkillCategory[]> {
  const skills = await request<DashboardSkill[]>('/api/skills')
  const groups = new Map<string, SkillInfo[]>()

  for (const skill of skills) {
    const key = displayCategory(skill.category)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push({
      name: skill.name,
      description: skill.description,
      enabled: skill.enabled,
    })
  }

  return Array.from(groups.entries()).map(([name, groupedSkills]) => ({
    name,
    description: name,
    skills: groupedSkills,
  }))
}

export async function fetchSkillContent(skillPath: string): Promise<string> {
  const [category, skill] = skillPath.split('/')
  return `# ${skill || skillPath}\n\n当前后端未开放技能文件内容读取。\n\n- 分类：${category || 'general'}\n- 标识：${skill || skillPath}\n`
}

export async function fetchSkillFiles(_category: string, _skill: string): Promise<SkillFileEntry[]> {
  return []
}

async function getWorkspaceFile(key: string): Promise<WorkspaceFile | null> {
  const res = await request<{ files: WorkspaceFile[] }>('/api/workspace/files')
  return (res.files || []).find((item) => item.key === key) || null
}

export async function fetchMemory(): Promise<MemoryData> {
  const [memory, user, soul] = await Promise.all([
    getWorkspaceFile('memory'),
    getWorkspaceFile('user'),
    getWorkspaceFile('soul'),
  ])

  return {
    memory: memory?.content || '',
    user: user?.content || '',
    soul: soul?.content || '',
    memory_mtime: null,
    user_mtime: null,
    soul_mtime: null,
  }
}

export async function saveMemory(section: 'memory' | 'user' | 'soul', content: string): Promise<void> {
  await request(`/api/workspace/files/${encodeURIComponent(section)}`, {
    method: 'PUT',
    body: JSON.stringify({ content }),
  })
}

export async function toggleSkill(name: string, enabled: boolean): Promise<void> {
  await request('/api/skills/toggle', {
    method: 'PUT',
    body: JSON.stringify({ name, enabled }),
  })
}
