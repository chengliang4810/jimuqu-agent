import { request } from '../client'

export interface AgentRun {
  run_id: string
  session_id: string
  source_key?: string
  status: string
  input_preview?: string
  final_reply_preview?: string
  provider?: string
  model?: string
  agent_name?: string
  agent_snapshot?: Record<string, unknown>
  attempts: number
  input_tokens: number
  output_tokens: number
  total_tokens: number
  started_at: number
  finished_at: number
  error?: string
}

export interface AgentRunEvent {
  event_id: string
  run_id: string
  session_id?: string
  source_key?: string
  event_type: string
  attempt_no: number
  provider?: string
  model?: string
  summary?: string
  created_at: number
  metadata?: Record<string, unknown>
}

export async function fetchSessionRuns(sessionId: string, limit = 20): Promise<AgentRun[]> {
  const res = await request<{ runs: AgentRun[] }>(`/api/sessions/${sessionId}/runs?limit=${limit}`)
  return res.runs || []
}

export async function fetchRunEvents(runId: string): Promise<AgentRunEvent[]> {
  const res = await request<{ events: AgentRunEvent[] }>(`/api/runs/${runId}/events`)
  return res.events || []
}
