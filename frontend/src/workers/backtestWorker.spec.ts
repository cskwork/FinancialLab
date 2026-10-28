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

// 31 candles: close 10 for the first 30, then 20 on the last one.
const candles = Array.from({ length: 31 }, (_, i) =>
  candle(`2024-01-${String(i + 1).padStart(2, '0')}`, i === 30 ? 20 : 10)
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

  it('buys a fixed amount every 30 candles for MONTHLY DCA', () => {
    const { data } = run(candles)

    // Buys at index 0 (close 10) and index 30 (close 20): 10 + 5 shares for 200 invested.
    expect(data.dcaStrategy.trades.map((t: { date: string }) => t.date)).toEqual(['2024-01-01', '2024-01-31'])
    expect(data.dcaStrategy.totalInvested).toBe(200)
    expect(data.dcaStrategy.totalShares).toBe(15)
    expect(data.dcaStrategy.finalValue).toBe(300)
    expect(data.dcaStrategy.avgCost).toBeCloseTo(13.3333, 4)
    expect(data.dcaStrategy.returnPercentage).toBe(50)
  })

  it('steps by 7 candles (not calendar weeks) for WEEKLY DCA', () => {
    const { data } = run(candles, 'WEEKLY')

    expect(data.dcaStrategy.trades).toHaveLength(5) // indices 0, 7, 14, 21, 28
    expect(data.dcaStrategy.totalInvested).toBe(500)
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
