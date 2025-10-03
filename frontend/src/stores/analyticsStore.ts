/**
 * Portfolio Analytics Store
 * 보유 종목 요약과 리스크 지표 조회 (Web Worker 활용)
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'
import Decimal from 'decimal.js'
import { apiClient } from '@/services/api'
import type {
  HoldingResponse,
  MetricSnapshotResponse
} from '@/services/api/schemas'
import { useAuthStore } from './authStore'
import { useConfigStore } from './configStore'
import { db } from '@/services/db/schema'

interface MetricsWorkerResult {
  sharpeRatio: number
  volatility: number
  maxDrawdown: number
  avgReturn: number
  stdDev: number
}

async function buildWeightedReturns(holdings: HoldingResponse[]): Promise<number[]> {
  if (holdings.length === 0) {
    return []
  }

  const totalValue = holdings.reduce((acc, holding) => acc + holding.marketValue, 0)
  if (totalValue === 0) return []

  const returnsByDate = new Map<string, number>()

  for (const holding of holdings) {
    const candles = await db.candles.where('symbol').equals(holding.symbol).sortBy('date')
    const weight = holding.marketValue / totalValue

    for (let i = 1; i < candles.length; i++) {
      const prev = candles[i - 1]
      const curr = candles[i]
      if (!prev || !curr || prev.close === 0) continue

      const dailyReturn = ((curr.close - prev.close) / prev.close) * 100
      const current = returnsByDate.get(curr.date) ?? 0
      returnsByDate.set(curr.date, current + dailyReturn * weight)
    }
  }

  return Array.from(returnsByDate.entries())
    .sort(([a], [b]) => (a < b ? -1 : 1))
    .map(([, value]) => value)
}

function runMetricsWorker(returns: number[]): Promise<MetricsWorkerResult> {
  return new Promise((resolve, reject) => {
    const worker = new Worker(new URL('../workers/metricsWorker.ts', import.meta.url), { type: 'module' })

    worker.onmessage = (event: MessageEvent<{ type: string; data?: MetricsWorkerResult; error?: string }>) => {
      if (event.data.type === 'success' && event.data.data) {
        resolve(event.data.data)
      } else if (event.data.type === 'error') {
        reject(new Error(event.data.error ?? 'Metrics worker failed'))
      }
      worker.terminate()
    }

    worker.onerror = (error) => {
      worker.terminate()
      reject(error)
    }

    worker.postMessage({ type: 'calculateMetrics', params: { returns } })
  })
}

function composeMetricSnapshot(
  portfolioId: number,
  totalValue: number,
  returns: number[],
  workerResult: MetricsWorkerResult
): MetricSnapshotResponse {
  const pnlDaily = returns.at(-1) ? (returns.at(-1)! / 100) * totalValue : 0
  const cumulativeReturn = returns.reduce(
    (acc, ret) => acc.times(new Decimal(1).plus(ret / 100)),
    new Decimal(1)
  )
  const pnlYTD = cumulativeReturn.minus(1).times(totalValue).toNumber()

  return {
    id: Number(new Date().getTime()),
    portfolioId,
    asOf: new Date().toISOString(),
    totalValue,
    cash: 0,
    pnlDaily,
    pnlYTD,
    volatility: workerResult.volatility,
    sharpe: workerResult.sharpeRatio,
    maxDrawdown: workerResult.maxDrawdown
  }
}

export const useAnalyticsStore = defineStore('analytics', () => {
  const authStore = useAuthStore()
  const configStore = useConfigStore()

  const holdings = ref<HoldingResponse[]>([])
  const metrics = ref<MetricSnapshotResponse | null>(null)
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function fetchAnalytics(portfolioId: number) {
    if (!authStore.token) {
      error.value = '로그인이 필요합니다'
      return
    }

    isLoading.value = true
    error.value = null

    try {
      const holdingsResponse = await apiClient.getHoldings(portfolioId, authStore.token)
      if (!holdingsResponse.success || !holdingsResponse.data) {
        error.value = holdingsResponse.error?.message ?? '보유 종목 정보를 불러오지 못했습니다'
        return
      }

      holdings.value = holdingsResponse.data
      const totalValue = holdings.value.reduce((acc, holding) => acc + holding.marketValue, 0)

      if (configStore.isMockMode) {
        const returns = await buildWeightedReturns(holdings.value)
        const workerMetrics = await runMetricsWorker(returns)
        metrics.value = composeMetricSnapshot(portfolioId, totalValue, returns, workerMetrics)
      } else {
        const metricsResponse = await apiClient.getPortfolioMetrics(portfolioId, authStore.token)
        if (metricsResponse.success && metricsResponse.data) {
          metrics.value = metricsResponse.data
        } else {
          error.value = metricsResponse.error?.message ?? '리스크 지표를 불러오지 못했습니다'
        }
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : '지표 조회 중 오류'
    } finally {
      isLoading.value = false
    }
  }

  function reset() {
    holdings.value = []
    metrics.value = null
    error.value = null
  }

  return {
    holdings,
    metrics,
    isLoading,
    error,
    fetchAnalytics,
    reset
  }
})
