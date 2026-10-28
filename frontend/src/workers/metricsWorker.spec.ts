import { describe, it, expect, vi, beforeAll, beforeEach } from 'vitest'

// Same harness as backtestWorker.spec.ts: drive the worker through `self` and capture `postMessage`.
const replies = vi.fn()

beforeAll(async () => {
  vi.stubGlobal('postMessage', replies)
  await import('./metricsWorker')
})

beforeEach(() => {
  replies.mockClear()
})

function run(returns: number[], riskFreeRate?: number) {
  self.dispatchEvent(
    new MessageEvent('message', { data: { type: 'calculateMetrics', params: { returns, riskFreeRate } } })
  )
  expect(replies).toHaveBeenCalledTimes(1)
  return replies.mock.calls[0]![0]
}

describe('metricsWorker', () => {
  it('annualises daily volatility with sqrt(252)', () => {
    // Daily returns in percent: mean 0, population std dev 1.
    const { type, data } = run([1, -1, 1, -1])

    expect(type).toBe('success')
    expect(data.avgReturn).toBe(0)
    expect(data.stdDev).toBe(1)
    expect(data.volatility).toBeCloseTo(Math.sqrt(252), 10)
  })

  it('computes an annualised Sharpe ratio from mean excess return over std dev', () => {
    // Mean 1, std dev 1, no risk-free rate: (1 / 1) * sqrt(252).
    const { data } = run([2, 0], 0)

    expect(data.sharpeRatio).toBeCloseTo(Math.sqrt(252), 10)
  })

  it('converts the annual decimal risk-free rate to daily percent', () => {
    // [2, 0]%: mean = 1%, population std dev = 1%; 2% / 252 = 0.0079365...% daily.
    // Sharpe = (1 - 2/252) / 1 * sqrt(252) = 15.7485197087...
    expect(run([2, 0]).data.sharpeRatio).toBeCloseTo(15.7485197087, 10)
    replies.mockClear()
    // 25.2% annual / 252 = 0.1% daily, so Sharpe = 0.9 * sqrt(252).
    expect(run([2, 0], 0.252).data.sharpeRatio).toBeCloseTo(0.9 * Math.sqrt(252), 10)
  })

  it('returns a Sharpe ratio of 0 when returns have no variance', () => {
    const { data } = run([0.5, 0.5, 0.5])

    expect(data.stdDev).toBe(0)
    expect(data.sharpeRatio).toBe(0)
  })

  it('measures max drawdown from the compounded peak', () => {
    // +10% then -50%: equity 1.0 -> 1.1 -> 0.55, so the drawdown from the 1.1 peak is 50%.
    const { data } = run([10, -50])

    expect(data.maxDrawdown).toBeCloseTo(50, 10)
  })

  it('replies with an error when there are no returns', () => {
    const reply = run([])

    expect(reply.type).toBe('error')
    expect(reply.error).toBe('No returns data')
  })
})
