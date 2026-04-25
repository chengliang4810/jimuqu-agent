import { request } from '../client'

export interface DailyUsageItem {
  day: string
  input_tokens: number
  output_tokens: number
  cache_read_tokens: number
  reasoning_tokens: number
  estimated_cost: number
  actual_cost: number
  sessions: number
}

export interface ModelUsageItem {
  model: string
  input_tokens: number
  output_tokens: number
  estimated_cost: number
  sessions: number
}

export interface UsageTotals {
  total_input: number
  total_output: number
  total_cache_read: number
  total_reasoning: number
  total_estimated_cost: number
  total_actual_cost: number
  total_sessions: number
}

export interface UsageAnalytics {
  daily: DailyUsageItem[]
  by_model: ModelUsageItem[]
  totals: UsageTotals
}

export async function fetchUsageAnalytics(days = 30): Promise<UsageAnalytics> {
  return request<UsageAnalytics>(`/api/analytics/usage?days=${days}`)
}
