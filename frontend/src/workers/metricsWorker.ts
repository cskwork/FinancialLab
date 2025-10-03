/**
 * Metrics Worker
 * Sharpe ratio, Volatility, Max Drawdown 계산
 */

import Decimal from 'decimal.js'

// Worker 메시지 타입
interface WorkerRequest {
  type: 'calculateMetrics'
  params: MetricsParams
}

interface MetricsParams {
  returns: number[] // 일별 수익률 (%)
  riskFreeRate?: number // 연간 무위험 수익률 (기본 0.02 = 2%)
}

interface WorkerResponse {
  type: 'success' | 'error'
  data?: MetricsResult
  error?: string
}

interface MetricsResult {
  sharpeRatio: number
  volatility: number // 연율화된 변동성 (%)
  maxDrawdown: number // 최대 낙폭 (%)
  avgReturn: number // 평균 수익률 (%)
  stdDev: number // 표준편차
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

/**
 * 메트릭 계산
 */
function calculateMetrics(params: MetricsParams): MetricsResult {
  const { returns, riskFreeRate = 0.02 } = params

  if (returns.length === 0) {
    throw new Error('No returns data')
  }

  // 평균 수익률
  const avgReturn = calculateAverage(returns)

  // 표준편차
  const stdDev = calculateStdDev(returns, avgReturn)

  // 연율화된 변동성 (일별 → 연간: sqrt(252))
  const volatility = stdDev * Math.sqrt(252)

  // Sharpe ratio
  const sharpeRatio = calculateSharpeRatio(avgReturn, stdDev, riskFreeRate)

  // 최대 낙폭
  const maxDrawdown = calculateMaxDrawdown(returns)

  return {
    sharpeRatio,
    volatility,
    maxDrawdown,
    avgReturn,
    stdDev
  }
}

/**
 * 평균 계산
 */
function calculateAverage(values: number[]): number {
  const sum = values.reduce((acc, val) => acc + val, 0)
  return sum / values.length
}

/**
 * 표준편차 계산
 */
function calculateStdDev(values: number[], mean: number): number {
  const variance = values.reduce((acc, val) => {
    const diff = val - mean
    return acc + diff * diff
  }, 0) / values.length

  return Math.sqrt(variance)
}

/**
 * Sharpe ratio 계산
 * (평균 수익률 - 무위험 수익률) / 표준편차
 */
function calculateSharpeRatio(
  avgReturn: number,
  stdDev: number,
  riskFreeRate: number
): number {
  if (stdDev === 0) return 0

  // 연율화된 Sharpe ratio
  const dailyRiskFreeRate = riskFreeRate / 252
  const excessReturn = avgReturn - dailyRiskFreeRate

  return (excessReturn / stdDev) * Math.sqrt(252)
}

/**
 * 최대 낙폭 (Max Drawdown) 계산
 */
function calculateMaxDrawdown(returns: number[]): number {
  let peak = new Decimal(1)
  let maxDrawdown = new Decimal(0)

  let cumReturn = new Decimal(1)

  for (const ret of returns) {
    // 누적 수익률 (곱셈 방식)
    cumReturn = cumReturn.times(new Decimal(1).plus(ret / 100))

    // 새로운 peak 업데이트
    if (cumReturn.greaterThan(peak)) {
      peak = cumReturn
    }

    // Drawdown 계산
    const drawdown = peak.minus(cumReturn).dividedBy(peak).times(100)

    if (drawdown.greaterThan(maxDrawdown)) {
      maxDrawdown = drawdown
    }
  }

  return maxDrawdown.toNumber()
}

export {}
