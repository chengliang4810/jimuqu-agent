import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import { afterEach, describe, expect, it } from 'vitest'

import { resolveDashboardToken } from '../lib/dashboardToken.js'

/** 记录测试创建的临时目录，确保每条用例后独立清理。 */
const temporaryDirectories: string[] = []

/** 创建带 config.yml 的临时工作区。 */
const workspaceWithConfig = (content: string): string => {
  const root = mkdtempSync(join(tmpdir(), 'solonclaw-tui-token-'))
  const workspace = join(root, 'workspace')
  mkdirSync(workspace)
  writeFileSync(join(workspace, 'config.yml'), content, 'utf8')
  temporaryDirectories.push(root)

  return workspace
}

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { force: true, recursive: true })
  }
})

describe('resolveDashboardToken', () => {
  it('优先使用显式 canonical 环境变量', () => {
    const workspace = workspaceWithConfig('solonclaw:\n  dashboard:\n    accessToken: file-token\n')

    expect(
      resolveDashboardToken('http://127.0.0.1:8080', {
        env: {
          SOLONCLAW_DASHBOARD_ACCESS_TOKEN: 'canonical-token',
          SOLONCLAW_DASHBOARD_TOKEN: 'legacy-token',
          SOLONCLAW_WORKSPACE: workspace
        }
      })
    ).toBe('canonical-token')
  })

  it('本地连接从明确工作区读取嵌套配置 token', () => {
    const workspace = workspaceWithConfig('solonclaw:\n  dashboard:\n    accessToken: local-file-token\n')

    expect(
      resolveDashboardToken('http://localhost:8080', {
        env: { SOLONCLAW_WORKSPACE: workspace }
      })
    ).toBe('local-file-token')
  })

  it('支持 Docker 使用 SOLONCLAW_HOME 直接指向工作区', () => {
    const workspace = workspaceWithConfig('"solonclaw.dashboard.accessToken": docker-file-token\n')

    expect(
      resolveDashboardToken('http://[::1]:8080', {
        env: { SOLONCLAW_HOME: workspace }
      })
    ).toBe('docker-file-token')
  })

  it('环境变量未生效时读取默认用户工作区', () => {
    const home = mkdtempSync(join(tmpdir(), 'solonclaw-tui-home-'))
    const workspace = join(home, '.solonclaw', 'workspace')
    mkdirSync(workspace, { recursive: true })
    writeFileSync(
      join(workspace, 'config.yml'),
      'solonclaw:\n  dashboard:\n    accessToken: default-home-token\n',
      'utf8'
    )
    temporaryDirectories.push(home)

    expect(
      resolveDashboardToken('http://127.0.0.1:8080', {
        env: {},
        home,
        cwd: join(home, 'unrelated')
      })
    ).toBe('default-home-token')
  })

  it('远程连接不会读取或发送本机配置 token', () => {
    const workspace = workspaceWithConfig('solonclaw:\n  dashboard:\n    accessToken: local-only-token\n')

    expect(
      resolveDashboardToken('https://agent.example.com', {
        env: { SOLONCLAW_WORKSPACE: workspace }
      })
    ).toBe('')
  })

  it('拒绝通过远程明文 HTTP 发送显式 token', () => {
    expect(() =>
      resolveDashboardToken('http://agent.example.com:8080', {
        env: { SOLONCLAW_DASHBOARD_ACCESS_TOKEN: 'remote-token' }
      })
    ).toThrow(/HTTPS.*SSH/)
  })

  it('远程 HTTPS 允许发送显式 token', () => {
    expect(
      resolveDashboardToken('https://agent.example.com', {
        env: { SOLONCLAW_DASHBOARD_ACCESS_TOKEN: 'remote-token' }
      })
    ).toBe('remote-token')
  })

  it('名称以 127 开头的远程域名不会被误判为 loopback', () => {
    const workspace = workspaceWithConfig('solonclaw:\n  dashboard:\n    accessToken: local-only-token\n')

    expect(
      resolveDashboardToken('https://127.attacker.example', {
        env: { SOLONCLAW_WORKSPACE: workspace }
      })
    ).toBe('')
  })

  it('格式错误的本地配置按未配置处理', () => {
    const workspace = workspaceWithConfig('solonclaw: [unterminated\n')

    expect(
      resolveDashboardToken('http://127.0.0.1:8080', {
        env: { SOLONCLAW_WORKSPACE: workspace }
      })
    ).toBe('')
  })
})
