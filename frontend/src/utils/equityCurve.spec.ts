import { describe, it, expect } from 'vitest'
import { buildEquityCurves } from './equityCurve'

const candles = [
  { date: '2024-01-03', close: 12 },
  { date: '2024-01-01', close: 10 },
  { date: '2024-01-02', close: 8 }
]

describe('buildEquityCurves', () => {
  it('values each strategy at every close, in date order', () => {
    const points = buildEquityCurves(
      candles,
      [
        { date: '2024-01-01', shares: 10, cost: 100 },
        { date: '2024-01-02', shares: 12.5, cost: 100 }
      ],
      [{ date: '2024-01-01', shares: 20, cost: 200 }]
    )

    expect(points.map((p) => p.date)).toEqual(['2024-01-01', '2024-01-02', '2024-01-03'])
    expect(points[0]).toEqual({ date: '2024-01-01', dcaValue: 100, dcaInvested: 100, lumpValue: 200, lumpInvested: 200 })
    expect(points[1]).toMatchObject({ dcaValue: 22.5 * 8, dcaInvested: 200, lumpValue: 160 })
    expect(points[2]).toMatchObject({ dcaValue: 22.5 * 12, dcaInvested: 200, lumpValue: 240 })
  })

  it('ends at the same final values the worker reports', () => {
    const points = buildEquityCurves(candles, [{ date: '2024-01-01', shares: 5, cost: 50 }], [])
    const last = points.at(-1)!
    expect(last.dcaValue).toBe(5 * 12)
    expect(last.lumpValue).toBe(0)
  })

  it('returns an empty curve without candles', () => {
    expect(buildEquityCurves([], [], [])).toEqual([])
  })
})
