export type PersonaMeta = {
  fileName: string
}

const PERSONA_META: Record<string, PersonaMeta> = {
  agents: {
    fileName: 'AGENTS.md',
  },
  memory: {
    fileName: 'MEMORY.md',
  },
  memory_today: {
    fileName: 'memory/YYYY-MM-DD.md',
  },
  soul: {
    fileName: 'SOUL.md',
  },
  identity: {
    fileName: 'IDENTITY.md',
  },
  user: {
    fileName: 'USER.md',
  },
  tools: {
    fileName: 'TOOLS.md',
  },
  heartbeat: {
    fileName: 'HEARTBEAT.md',
  },
}

export function getPersonaMeta(key: string): PersonaMeta {
  return PERSONA_META[key] || {
    fileName: key,
  }
}
