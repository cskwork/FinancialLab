import Decimal from 'decimal.js'

export interface MetricsParams {
  returns: number[] // Daily returns in percent: 1 means 1%.
  riskFreeRate?: number // Annual decimal rate: 0.02 means 2% (252 trading days).
}

export interface MetricsResult {
  sharpeRatio: number
  volatility: number // Annualized percent.
  maxDrawdown: number // Percent.
  avgReturn: number // Daily percent.
  stdDev: number // Daily percent, population standard deviation.
}

/**
 * 메트릭 계산
 */
export function calculateMetrics(params: MetricsParams): MetricsResult {
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
  const dailyRiskFreeRate = riskFreeRate * 100 / 252
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

