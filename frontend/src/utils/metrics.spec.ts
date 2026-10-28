import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { calculateMetrics } from './metrics'

describe('shared metrics', () => {
  it('calculates percent-based metrics with a decimal annual risk-free rate', () => {
    // [2, 0]%: mean 1%, population variance ((2-1)^2 + (0-1)^2)/2 = 1.
    // Annual risk-free 0.252 = 25.2%; daily = 0.1%; Sharpe = (1 - 0.1)/1 * sqrt(252).
    const metrics = calculateMetrics({ returns: [2, 0], riskFreeRate: 0.252 })
    expect(metrics.avgReturn).toBe(1)
    expect(metrics.stdDev).toBe(1)
    expect(metrics.volatility).toBeCloseTo(Math.sqrt(252), 10)
    expect(metrics.sharpeRatio).toBeCloseTo(0.9 * Math.sqrt(252), 10)
    expect(metrics.maxDrawdown).toBe(0)
  })

  it('rejects missing returns and handles zero variance', () => {
    expect(() => calculateMetrics({ returns: [] })).toThrow('No returns data')
    expect(calculateMetrics({ returns: [0, 0] }).sharpeRatio).toBe(0)
  })
  it('matches the bundled demo portfolio Sharpe using latest market-value weights', () => {
    const returnsByDate = new Map<string, number>()
    for (const [symbol, value] of [['AAPL', 19176.3], ['SPY', 12754]] as const) {
      const rows = readFileSync(`public/data/ohlcv/${symbol}.csv`, 'utf8').trim().split('\n').slice(1)
        .map((line) => { const fields = line.split(','); return { date: fields[0]!, close: Number(fields[4]) } })
      for (let i = 1; i < rows.length; i++) {
        const prev = rows[i - 1]!, curr = rows[i]!
        const ret = (curr.close - prev.close) / prev.close * 100 * value / 31930.3
        returnsByDate.set(curr.date, (returnsByDate.get(curr.date) ?? 0) + ret)
      }
    }
    const returns = [...returnsByDate].sort(([a], [b]) => a.localeCompare(b)).map(([, ret]) => ret)
    expect(returns).toHaveLength(61)
    expect(calculateMetrics({ returns }).sharpeRatio).toBeCloseTo(5.4106641762, 10)
  })

})
