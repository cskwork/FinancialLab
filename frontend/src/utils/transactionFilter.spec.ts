import { describe, it, expect } from 'vitest'
import { filterTransactions, countByType, filterFromQuery, filterToQuery } from './transactionFilter'

const list = [
  { id: 1, type: 'BUY' as const, symbol: 'AAPL', note: '첫 AAPL 매수' },
  { id: 2, type: 'BUY' as const, symbol: 'SPY', note: 'SPY ETF 매수' },
  { id: 3, type: 'SELL' as const, symbol: 'AAPL', note: 'Trim position' },
  { id: 4, type: 'DEPOSIT' as const, note: 'Initial deposit' }
]

describe('transactionFilter', () => {
  it('filters by type', () => {
    expect(filterTransactions(list, { type: 'BUY', query: '' }).map((t) => t.id)).toEqual([1, 2])
    expect(filterTransactions(list, { type: 'ALL', query: '' })).toHaveLength(4)
  })

  it('searches symbol and note case-insensitively, combined with type', () => {
    expect(filterTransactions(list, { type: 'ALL', query: 'aapl' }).map((t) => t.id)).toEqual([1, 3])
    expect(filterTransactions(list, { type: 'SELL', query: 'aapl' }).map((t) => t.id)).toEqual([3])
    expect(filterTransactions(list, { type: 'ALL', query: '  deposit ' }).map((t) => t.id)).toEqual([4])
    expect(filterTransactions(list, { type: 'ALL', query: '매수' }).map((t) => t.id)).toEqual([1, 2])
  })

  it('counts each type', () => {
    expect(countByType(list)).toEqual({ ALL: 4, BUY: 2, SELL: 1, DEPOSIT: 1, WITHDRAW: 0 })
  })

  it('round-trips through the URL query and rejects unknown types', () => {
    expect(filterFromQuery({ type: 'sell', q: 'aapl' })).toEqual({ type: 'SELL', query: 'aapl' })
    expect(filterFromQuery({ type: 'HACK' })).toEqual({ type: 'ALL', query: '' })
    expect(filterToQuery({ type: 'ALL', query: ' ' })).toEqual({})
    expect(filterToQuery({ type: 'BUY', query: 'spy' })).toEqual({ type: 'BUY', q: 'spy' })
  })
})
