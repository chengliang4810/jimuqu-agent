import { readFileSync, statSync } from 'node:fs'
import { homedir } from 'node:os'
import { resolve } from 'node:path'

import { parse } from 'yaml'

/** 限制自动读取的本地配置文件大小，避免异常文件拖慢 TUI 启动。 */
const MAX_CONFIG_BYTES = 1024 * 1024

/** Dashboard token 自动发现所需的可测试运行环境。 */
type DashboardTokenOptions = {
  /** 覆盖当前工作目录，供启动器布局测试使用。 */
  cwd?: string
  /** 覆盖进程环境，供候选路径和显式 token 优先级测试使用。 */
  env?: NodeJS.ProcessEnv
  /** 覆盖当前用户主目录，供默认安装布局测试使用。 */
  home?: string
}

/** 将未知 YAML 节点收窄为普通对象。 */
const asRecord = (value: unknown): Record<string, unknown> =>
  value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : {}

/** 判断 TUI 目标是否为明确的 HTTP loopback 地址。 */
const isLoopbackServer = (serverUrl: string): boolean => {
  try {
    const url = new URL(serverUrl)
    const hostname = url.hostname.toLowerCase()

    return (
      (url.protocol === 'http:' || url.protocol === 'https:') &&
      (hostname === 'localhost' || hostname === '[::1]' || /^127(?:\.\d{1,3}){3}$/.test(hostname))
    )
  } catch {
    return false
  }
}

/** 按当前安装布局生成可能的工作区配置文件路径，并保持确定性优先级。 */
const configCandidates = (env: NodeJS.ProcessEnv, cwd: string, defaultHome: string): string[] => {
  const candidates: string[] = []

  /** 将候选目录解析到配置文件，并去除空目录和重复路径。 */
  const addWorkspace = (directory: string | undefined) => {
    const normalized = directory?.trim()

    if (normalized) {
      candidates.push(resolve(cwd, normalized, 'config.yml'))
    }
  }

  addWorkspace(env.SOLONCLAW_WORKSPACE)

  const installHome = env.SOLONCLAW_HOME?.trim()

  if (installHome) {
    addWorkspace(installHome)
    addWorkspace(resolve(installHome, 'workspace'))
  }

  addWorkspace(resolve(cwd, 'workspace'))
  addWorkspace(resolve(defaultHome, '.solonclaw', 'workspace'))

  return [...new Set(candidates)]
}

/** 从已解析配置中读取 Dashboard accessToken，兼容嵌套与点号键形式。 */
const tokenFromConfig = (config: unknown): string => {
  const root = asRecord(config)
  const direct = root['solonclaw.dashboard.accessToken']

  if (typeof direct === 'string') {
    return direct.trim()
  }

  const solonclaw = asRecord(root.solonclaw)
  const dashboard = asRecord(solonclaw.dashboard)

  return typeof dashboard.accessToken === 'string' ? dashboard.accessToken.trim() : ''
}

/** 安全读取单个本地配置候选；缺失、过大或格式错误时按未配置处理。 */
const tokenFromFile = (path: string): string => {
  try {
    const stat = statSync(path)

    if (!stat.isFile() || stat.size > MAX_CONFIG_BYTES) {
      return ''
    }

    return tokenFromConfig(parse(readFileSync(path, 'utf8')))
  } catch {
    return ''
  }
}

/**
 * 解析 TUI 握手使用的 Dashboard token。
 *
 * 显式环境变量始终优先；只有连接明确 loopback 服务时才会读取本机工作区配置，
 * 防止把本机长期凭据自动发送给远程地址。
 */
export const resolveDashboardToken = (serverUrl: string, options: DashboardTokenOptions = {}): string => {
  const env = options.env ?? process.env
  const explicit = env.SOLONCLAW_DASHBOARD_ACCESS_TOKEN?.trim() || env.SOLONCLAW_DASHBOARD_TOKEN?.trim() || ''

  if (explicit || !isLoopbackServer(serverUrl)) {
    return explicit
  }

  const cwd = options.cwd ?? process.cwd()
  const home = options.home ?? homedir()

  for (const path of configCandidates(env, cwd, home)) {
    const token = tokenFromFile(path)

    if (token) {
      return token
    }
  }

  return ''
}
