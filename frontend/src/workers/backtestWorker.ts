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
  dcaAmount: number // 선택한 주기별 DCA 금액
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

  // Dates define the schedule; sort a copy so the caller's rows stay unchanged.
  const orderedCandles = [...candles].sort((a, b) => a.date.localeCompare(b.date))

  // DCA 전략
  const dcaResult = simulateDCA(orderedCandles, dcaAmount, dcaInterval)

  // Lump-sum 전략
  const lumpSumResult = simulateLumpSum(orderedCandles, initialCapital)

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

// An ISO week starts on Monday. Its Monday date is a unique key even across year boundaries.
function calendarPeriod(date: string, interval: 'WEEKLY' | 'MONTHLY'): string {
  if (interval === 'MONTHLY') return date.slice(0, 7)
  const day = new Date(`${date}T00:00:00Z`)
  day.setUTCDate(day.getUTCDate() - (day.getUTCDay() + 6) % 7)
  return day.toISOString().slice(0, 10)
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

  let previousPeriod: string | undefined
  for (const candle of candles) {
    const period = calendarPeriod(candle.date, interval)
    if (period === previousPeriod) continue
    previousPeriod = period

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
