import { request } from '../client'

export interface HealthResponse {
  status: string
  version?: string
  webui_version?: string
  webui_latest?: string
  webui_update_available?: boolean
  node_version?: string
}

export interface ModelInfo {
  id: string
  label: string
}

export interface ModelGroup {
  provider: string
  models: ModelInfo[]
}

export interface ConfigModelsResponse {
  default: string
  groups: ModelGroup[]
}

export interface AvailableModelGroup {
  provider: string
  label: string
  base_url: string
  models: string[]
  api_key: string
}

export interface AvailableModelsResponse {
  default: string
  default_provider: string
  groups: AvailableModelGroup[]
  allProviders: AvailableModelGroup[]
}

export interface CustomProvider {
  name: string
  base_url: string
  api_key: string
  model: string
  providerKey?: string | null
}

interface DashboardStatus {
  version?: string
  update_available?: boolean
}

interface DashboardModelInfo {
  model: string
  provider: string
}

export async function checkHealth(): Promise<HealthResponse> {
  const [health, status] = await Promise.all([
    request<{ ok?: boolean; service?: string }>('/health'),
    request<DashboardStatus>('/api/status'),
  ])

  return {
    status: health.ok ? 'ok' : 'error',
    version: status.version,
    webui_version: status.version,
    webui_latest: status.version,
    webui_update_available: !!status.update_available,
    node_version: '',
  }
}

export async function triggerUpdate(): Promise<{ success: boolean; message: string }> {
  return {
    success: false,
    message: '当前后端未开放在线更新',
  }
}

export async function fetchConfigModels(): Promise<ConfigModelsResponse> {
  const info = await request<DashboardModelInfo>('/api/model/info')
  return {
    default: info.model,
    groups: [
      {
        provider: info.provider,
        models: [{ id: info.model, label: info.model }],
      },
    ],
  }
}

export async function fetchAvailableModels(): Promise<AvailableModelsResponse> {
  const info = await request<DashboardModelInfo & { auto_context_length?: number }>('/api/model/info')
  const group: AvailableModelGroup = {
    provider: info.provider,
    label: info.provider,
    base_url: '',
    models: [info.model],
    api_key: '',
  }

  return {
    default: info.model,
    default_provider: info.provider,
    groups: [group],
    allProviders: [group],
  }
}

export async function updateDefaultModel(data: {
  default: string
  provider?: string
  base_url?: string
  api_key?: string
}): Promise<void> {
  const current = await request<Record<string, any>>('/api/config')
  const next = {
    ...current,
    llm: {
      ...(current.llm || {}),
      model: data.default,
      provider: data.provider || current.llm?.provider,
      apiUrl: data.base_url || current.llm?.apiUrl,
    },
  }

  await request('/api/config', {
    method: 'PUT',
    body: JSON.stringify({ config: next }),
  })
}

function unsupported(): never {
  throw new Error('当前后端未开放自定义 provider 管理')
}

export async function addCustomProvider(_data: CustomProvider): Promise<void> {
  unsupported()
}

export async function removeCustomProvider(_name: string): Promise<void> {
  unsupported()
}

export async function updateProvider(_poolKey: string, data: {
  name?: string
  base_url?: string
  api_key?: string
  model?: string
}): Promise<void> {
  if (!data.model) {
    throw new Error('当前只支持切换默认模型')
  }
  await updateDefaultModel({
    default: data.model,
    base_url: data.base_url,
    api_key: data.api_key,
  })
}
