/**
 * Transaction filter
 * 거래 내역 화면의 유형 필터 + 검색(심볼/메모)을 위한 순수 함수.
 * URL 쿼리(?type=BUY&q=aapl)와 상호 변환해 새로고침/공유 시에도 필터가 유지된다.
 */

export const TRANSACTION_TYPES = ['BUY', 'SELL', 'DEPOSIT', 'WITHDRAW'] as const
export type TransactionType = (typeof TRANSACTION_TYPES)[number]
export type TypeFilter = 'ALL' | TransactionType

export interface FilterableTransaction {
  type: TransactionType
  symbol?: string
  note?: string
}

export interface TransactionFilter {
  type: TypeFilter
  query: string
}

export function filterTransactions<T extends FilterableTransaction>(list: T[], filter: TransactionFilter): T[] {
  const needle = filter.query.trim().toLowerCase()
  return list.filter((tx) => {
    if (filter.type !== 'ALL' && tx.type !== filter.type) return false
    if (!needle) return true
    return (
      (tx.symbol ?? '').toLowerCase().includes(needle) ||
      (tx.note ?? '').toLowerCase().includes(needle)
    )
  })
}

export function countByType(list: FilterableTransaction[]): Record<TypeFilter, number> {
  const counts: Record<TypeFilter, number> = { ALL: list.length, BUY: 0, SELL: 0, DEPOSIT: 0, WITHDRAW: 0 }
  list.forEach((tx) => {
    counts[tx.type] += 1
  })
  return counts
}

export function filterFromQuery(query: Record<string, unknown>): TransactionFilter {
  const rawType = typeof query.type === 'string' ? query.type.toUpperCase() : ''
  const type: TypeFilter = (TRANSACTION_TYPES as readonly string[]).includes(rawType)
    ? (rawType as TransactionType)
    : 'ALL'
  const q = typeof query.q === 'string' ? query.q.slice(0, 80) : ''
  return { type, query: q }
}

export function filterToQuery(filter: TransactionFilter): Record<string, string> {
  const out: Record<string, string> = {}
  if (filter.type !== 'ALL') out.type = filter.type
  if (filter.query.trim()) out.q = filter.query.trim()
  return out
}
