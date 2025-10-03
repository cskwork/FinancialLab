/**
 * Backtest Worker
 * DCA vs Lump-sum 전략 시뮬레이션
 */

import Decimal from 'decimal.js'

// Worker 메시지 타입
interface WorkerRequest {
  type: 'runBacktest'
  params: BacktestParams
}

interface BacktestParams {
  symbol: string
  startDate: string
  endDate: string
  initialCapital: number
  dcaAmount: number // 월별 DCA 금액
  dcaInterval: 'WEEKLY' | 'MONTHLY'
  candles: CandleData[]
}

interface CandleData {
  date: string
  open: number
  high: number
  low: number
  close: number
  volume: number
}

interface WorkerResponse {
  type: 'success' | 'error' | 'progress'
  data?: BacktestResult
  error?: string
  progress?: number
}

interface BacktestResult {
  dcaStrategy: StrategyResult
  lumpSumStrategy: StrategyResult
  comparison: {
    dcaFinalValue: number
    lumpSumFinalValue: number
    dcaDifference: number
    lumpSumWins: boolean
  }
}

interface StrategyResult {
  totalInvested: number
  totalShares: number
  avgCost: number
  finalValue: number
  totalReturn: number
  returnPercentage: number
  trades: TradeRecord[]
}

interface TradeRecord {
  date: string
  shares: number
  price: number
  cost: number
}

// Worker 메시지 핸들러
self.addEventListener('message', (event: MessageEvent<WorkerRequest>) => {
  const { type, params } = event.data

  if (type === 'runBacktest') {
    try {
      const result = runBacktest(params)
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
 * 백테스트 실행
 */
function runBacktest(params: BacktestParams): BacktestResult {
  const { candles, initialCapital, dcaAmount, dcaInterval } = params

  // DCA 전략
  const dcaResult = simulateDCA(candles, dcaAmount, dcaInterval)

  // Lump-sum 전략
  const lumpSumResult = simulateLumpSum(candles, initialCapital)

  return {
    dcaStrategy: dcaResult,
    lumpSumStrategy: lumpSumResult,
    comparison: {
      dcaFinalValue: dcaResult.finalValue,
      lumpSumFinalValue: lumpSumResult.finalValue,
      dcaDifference: dcaResult.finalValue - lumpSumResult.finalValue,
      lumpSumWins: lumpSumResult.finalValue > dcaResult.finalValue
    }
  }
}

/**
 * DCA 전략 시뮬레이션
 */
function simulateDCA(
  candles: CandleData[],
  dcaAmount: number,
  interval: 'WEEKLY' | 'MONTHLY'
): StrategyResult {
  let totalInvested = new Decimal(0)
  let totalShares = new Decimal(0)
  const trades: TradeRecord[] = []

  // 간격에 따라 매수 (간단 구현: 매월 첫 번째 캔들)
  const step = interval === 'WEEKLY' ? 7 : 30

  for (let i = 0; i < candles.length; i += step) {
    const candle = candles[i]
    if (!candle) continue

    const price = new Decimal(candle.close)
    const shares = new Decimal(dcaAmount).dividedBy(price)

    totalInvested = totalInvested.plus(dcaAmount)
    totalShares = totalShares.plus(shares)

    trades.push({
      date: candle.date,
      shares: shares.toNumber(),
      price: price.toNumber(),
      cost: dcaAmount
    })
  }

  const lastCandle = candles[candles.length - 1]
  if (!lastCandle) throw new Error('No candles available')

  const finalPrice = new Decimal(lastCandle.close)
  const finalValue = totalShares.times(finalPrice)
  const avgCost = totalInvested.dividedBy(totalShares)
  const totalReturn = finalValue.minus(totalInvested)
  const returnPercentage = totalReturn.dividedBy(totalInvested).times(100)

  return {
    totalInvested: totalInvested.toNumber(),
    totalShares: totalShares.toNumber(),
    avgCost: avgCost.toNumber(),
    finalValue: finalValue.toNumber(),
    totalReturn: totalReturn.toNumber(),
    returnPercentage: returnPercentage.toNumber(),
    trades
  }
}

/**
 * Lump-sum 전략 시뮬레이션
 */
function simulateLumpSum(candles: CandleData[], initialCapital: number): StrategyResult {
  const firstCandle = candles[0]
  const lastCandle = candles[candles.length - 1]

  if (!firstCandle || !lastCandle) throw new Error('Insufficient candle data')

  const price = new Decimal(firstCandle.close)
  const shares = new Decimal(initialCapital).dividedBy(price)
  const finalPrice = new Decimal(lastCandle.close)
  const finalValue = shares.times(finalPrice)
  const totalReturn = finalValue.minus(initialCapital)
  const returnPercentage = totalReturn.dividedBy(initialCapital).times(100)

  return {
    totalInvested: initialCapital,
    totalShares: shares.toNumber(),
    avgCost: price.toNumber(),
    finalValue: finalValue.toNumber(),
    totalReturn: totalReturn.toNumber(),
    returnPercentage: returnPercentage.toNumber(),
    trades: [
      {
        date: firstCandle.date,
        shares: shares.toNumber(),
        price: price.toNumber(),
        cost: initialCapital
      }
    ]
  }
}

export {}
