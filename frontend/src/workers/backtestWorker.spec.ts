import { readFileSync } from 'node:fs'
import { describe, it, expect, vi, beforeAll, beforeEach } from 'vitest'

// The worker registers a 'message' listener on `self` and replies through the global `postMessage`.
// We drive it the same way the browser does, without changing the worker module.
const replies = vi.fn()

beforeAll(async () => {
  vi.stubGlobal('postMessage', replies)
  await import('./backtestWorker')
})

beforeEach(() => {
  replies.mockClear()
})

function candle(date: string, close: number) {
  return { date, open: close, high: close, low: close, close, volume: 0 }
}

function run(candles: ReturnType<typeof candle>[], dcaInterval: 'WEEKLY' | 'MONTHLY' = 'MONTHLY') {
  self.dispatchEvent(
    new MessageEvent('message', {
      data: {
        type: 'runBacktest',
        params: {
          symbol: 'TEST',
          startDate: candles[0]?.date ?? '',
          endDate: candles.at(-1)?.date ?? '',
          initialCapital: 1000,
          dcaAmount: 100,
          dcaInterval,
          candles
        }
      }
    })
  )
  expect(replies).toHaveBeenCalledTimes(1)
  return replies.mock.calls[0]![0]
}

// 31 candles: close 10 for the first 30, then 20 on February 1.
const candles = Array.from({ length: 31 }, (_, i) =>
  candle(i === 30 ? '2024-02-01' : `2024-01-${String(i + 1).padStart(2, '0')}`, i === 30 ? 20 : 10)
)

describe('backtestWorker', () => {
  it('buys the whole capital at the first close for lump-sum', () => {
    const { type, data } = run(candles)

    expect(type).toBe('success')
    expect(data.lumpSumStrategy).toMatchObject({
      totalInvested: 1000,
      totalShares: 100,
      avgCost: 10,
      finalValue: 2000,
      totalReturn: 1000,
      returnPercentage: 100
    })
  })

  it('buys on the first available date of each month for MONTHLY DCA', () => {
    const { data } = run(candles)

    // Buys at index 0 (close 10) and index 30 (close 20): 10 + 5 shares for 200 invested.
    expect(data.dcaStrategy.trades.map((t: { date: string }) => t.date)).toEqual(['2024-01-01', '2024-02-01'])
    expect(data.dcaStrategy.totalInvested).toBe(200)
    expect(data.dcaStrategy.totalShares).toBe(15)
    expect(data.dcaStrategy.finalValue).toBe(300)
    expect(data.dcaStrategy.avgCost).toBeCloseTo(13.3333, 4)
    expect(data.dcaStrategy.returnPercentage).toBe(50)
  })

  it('buys once per ISO calendar week for WEEKLY DCA', () => {
    const { data } = run(candles, 'WEEKLY')

    expect(data.dcaStrategy.trades).toHaveLength(5)
    expect(data.dcaStrategy.trades.map((t: { date: string }) => t.date)).toEqual([
      '2024-01-01', '2024-01-08', '2024-01-15', '2024-01-22', '2024-01-29'
    ])
    expect(data.dcaStrategy.totalInvested).toBe(500)
  })

  it('uses first trading days across a month boundary and the January 15 holiday gap', () => {
    const dates = ['2024-01-11', '2024-01-12', '2024-01-16', '2024-01-17',
      '2024-01-29', '2024-01-31', '2024-02-01', '2024-02-02', '2024-02-05']
    const rows = dates.map((date) => candle(date, 10))
    const weekly = run(rows, 'WEEKLY').data.dcaStrategy
    expect(weekly.trades.map((t: { date: string }) => t.date)).toEqual([
      '2024-01-11', '2024-01-16', '2024-01-29', '2024-02-05'
    ])
    expect(weekly.totalInvested).toBe(400)
    replies.mockClear()
    const monthly = run(rows).data.dcaStrategy
    expect(monthly.trades.map((t: { date: string }) => t.date)).toEqual(['2024-01-11', '2024-02-01'])
    expect(monthly.totalInvested).toBe(200)
  })

  it('keeps an ISO week together across the year boundary', () => {
    const rows = ['2020-12-30', '2020-12-31', '2021-01-01', '2021-01-04', '2021-01-05', '2021-01-11']
      .map((date) => candle(date, 10))
    expect(run(rows, 'WEEKLY').data.dcaStrategy.trades.map((t: { date: string }) => t.date))
      .toEqual(['2020-12-30', '2021-01-04', '2021-01-11'])
  })

  it('selects the earliest available price row even when input is unordered', () => {
    const rows = [candle('2024-02-02', 20), candle('2024-01-31', 10), candle('2024-02-01', 10)]
    const { data } = run(rows)
    expect(data.dcaStrategy.trades.map((t: { date: string }) => t.date)).toEqual(['2024-01-31', '2024-02-01'])
    expect(data.dcaStrategy.finalValue).toBe(400)
    expect(data.lumpSumStrategy.finalValue).toBe(2000)
    expect(rows[0]?.date).toBe('2024-02-02')
  })

  it('buys four calendar months in the bundled SPY demo', () => {
    const rows = readFileSync('public/data/ohlcv/SPY.csv', 'utf8').trim().split('\n').slice(1)
      .map((line) => { const [date, , , , close] = line.split(','); return candle(date!, Number(close)) })
    const { data } = run(rows)
    expect(data.dcaStrategy.trades.map((t: { date: string }) => t.date))
      .toEqual(['2024-01-02', '2024-02-01', '2024-03-01', '2024-04-01'])
    expect(data.dcaStrategy.totalInvested).toBe(400)
    expect(data.dcaStrategy.finalValue).toBeCloseTo(416.3068625664, 8)
  })

  it('reports which strategy ended higher', () => {
    const { data } = run(candles)

    expect(data.comparison).toEqual({
      dcaFinalValue: 300,
      lumpSumFinalValue: 2000,
      dcaDifference: -1700,
      lumpSumWins: true
    })
  })

  it('replies with an error message instead of throwing when there are no candles', () => {
    const reply = run([])

    expect(reply.type).toBe('error')
    expect(reply.error).toBe('No candles available')
  })
})
