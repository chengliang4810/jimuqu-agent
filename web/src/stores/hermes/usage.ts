import { fetchUsageAnalytics, type UsageAnalytics } from '@/api/hermes/usage'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

interface DailyUsage {
  date: string
  tokens: number
  cache: number
  sessions: number
  cost: number
}

interface ModelUsage {
  model: string
  inputTokens: number
  outputTokens: number
  cacheTokens: number
  totalTokens: number
  sessions: number
}

export const useUsageStore = defineStore('usage', () => {
  const analytics = ref<UsageAnalytics | null>(null)
  const isLoading = ref(false)

  async function loadUsage() {
    isLoading.value = true
    try {
      analytics.value = await fetchUsageAnalytics(30)
    } catch (err) {
      console.error('Failed to load usage analytics:', err)
    } finally {
      isLoading.value = false
    }
  }

  const totalInputTokens = computed(() => analytics.value?.totals.total_input || 0)

  const totalOutputTokens = computed(() => analytics.value?.totals.total_output || 0)

  const totalTokens = computed(() => totalInputTokens.value + totalOutputTokens.value)

  const totalSessions = computed(() => analytics.value?.totals.total_sessions || 0)

  const totalCacheTokens = computed(() => analytics.value?.totals.total_cache_read || 0)

  const cacheHitRate = computed(() => {
    const total = totalInputTokens.value
    if (total === 0) return null
    return ((totalCacheTokens.value / total) * 100)
  })

  const estimatedCost = computed(() => analytics.value?.totals.total_estimated_cost || 0)

  const modelUsage = computed<ModelUsage[]>(() => {
    return (analytics.value?.by_model || [])
      .map((item) => ({
        model: item.model || 'unknown',
        inputTokens: item.input_tokens || 0,
        outputTokens: item.output_tokens || 0,
        cacheTokens: 0,
        totalTokens: (item.input_tokens || 0) + (item.output_tokens || 0),
        sessions: item.sessions || 0,
      }))
      .sort((a, b) => b.totalTokens - a.totalTokens)
  })

  const dailyUsage = computed<DailyUsage[]>(() => {
    return (analytics.value?.daily || []).map((item) => ({
      date: item.day,
      tokens: (item.input_tokens || 0) + (item.output_tokens || 0),
      cache: item.cache_read_tokens || 0,
      sessions: item.sessions || 0,
      cost: item.actual_cost || item.estimated_cost || 0,
    }))
  })

  const avgSessionsPerDay = computed(() => {
    const days = Math.max(1, dailyUsage.value.length || 30)
    return totalSessions.value / days
  })

  return {
    analytics,
    isLoading,
    loadUsage,
    totalInputTokens,
    totalOutputTokens,
    totalTokens,
    totalSessions,
    totalCacheTokens,
    cacheHitRate,
    estimatedCost,
    modelUsage,
    dailyUsage,
    avgSessionsPerDay,
  }
})
