/**
 * Metrics Worker
 * Sharpe ratio, Volatility, Max Drawdown 계산
 */

import { calculateMetrics, type MetricsParams, type MetricsResult } from '@/utils/metrics'

// Worker 메시지 타입
interface WorkerRequest {
  type: 'calculateMetrics'
  params: MetricsParams
}

interface WorkerResponse {
  type: 'success' | 'error'
  data?: MetricsResult
  error?: string
}

// Worker 메시지 핸들러
self.addEventListener('message', (event: MessageEvent<WorkerRequest>) => {
  const { type, params } = event.data

  if (type === 'calculateMetrics') {
    try {
      const result = calculateMetrics(params)
      postMessage({
        type: 'success',
        data: result
      } as WorkerResponse)
    } catch (error) {
      postMessage({
        type: 'error',
        error: error instanceof Error ? error.message : 'Unknown error'
      } as WorkerResponse)
    }
  }
})
