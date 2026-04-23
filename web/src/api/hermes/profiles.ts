export interface HermesProfile {
  name: string
  active: boolean
  model: string
  gateway: string
  alias: string
}

export interface HermesProfileDetail {
  name: string
  path: string
  model: string
  provider: string
  gateway: string
  skills: number
  hasEnv: boolean
  hasSoulMd: boolean
}

const DEFAULT_PROFILE: HermesProfile = {
  name: 'default',
  active: true,
  model: 'gpt-5.4',
  gateway: 'local',
  alias: '默认配置',
}

export async function fetchProfiles(): Promise<HermesProfile[]> {
  return [DEFAULT_PROFILE]
}

export async function fetchProfileDetail(name: string): Promise<HermesProfileDetail> {
  return {
    name,
    path: 'runtime',
    model: 'gpt-5.4',
    provider: 'openai-responses',
    gateway: 'local',
    skills: 0,
    hasEnv: true,
    hasSoulMd: true,
  }
}

export async function createProfile(_name: string, _clone?: boolean): Promise<boolean> {
  return false
}

export async function deleteProfile(_name: string): Promise<boolean> {
  return false
}

export async function renameProfile(_name: string, _newName: string): Promise<boolean> {
  return false
}

export async function switchProfile(_name: string): Promise<boolean> {
  return true
}

export async function exportProfile(_name: string): Promise<boolean> {
  return false
}

export async function importProfile(_file: File): Promise<boolean> {
  return false
}
