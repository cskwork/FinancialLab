import { describe, it, beforeEach, expect } from 'vitest'
import { db } from '@/services/db/schema'
import { mockApiClient } from '@/services/api'
import { portfolioRepository, userRepository, transactionRepository } from '@/services/db/repositories'
import Papa from 'papaparse'
import {
  importTransactions,
  orderForImport,
  parseCsvRows,
  summarizeImport,
  toImportRequests,
  type CsvTransactionRow,
  type ImportPersist,
  type RejectedImportRow
} from './csvImport'

const persistViaApi: ImportPersist = async (request) => {
  const response = await mockApiClient.createTransaction(request)
  return response.success ? null : (response.error?.message ?? 'failed')
}

async function seedEmptyPortfolio() {
  const userId = await userRepository.create('csv@finlab.dev', 'USER')
  return await portfolioRepository.create({ userId, name: 'CSV Portfolio', baseCurrency: 'USD' })
}

describe('csvImport', () => {
  beforeEach(async () => {
    await db.users.clear()
    await db.portfolios.clear()
    await db.transactions.clear()
    await db.candles.clear()
  })

  it('imports a newest-first CSV so the SELL lands after its older BUY', async () => {
    const portfolioId = await seedEmptyPortfolio()
    // 증권사 내보내기처럼 최신 거래가 먼저 오는 파일
    const rows = [
      { type: 'SELL', symbol: 'AAPL', qty: 4, price: 120, fee: 0, timestamp: '2024-01-03T15:00:00.000Z' },
      { type: 'BUY', symbol: 'AAPL', qty: 10, price: 100, fee: 1, timestamp: '2024-01-01T15:00:00.000Z' }
    ]

    const result = await importTransactions(toImportRequests(rows, portfolioId), persistViaApi)

    expect(result.created).toBe(2)
    expect(result.rejected).toEqual([])
    expect(await transactionRepository.findByPortfolioId(portfolioId)).toHaveLength(2)
    const holdings = await mockApiClient.getHoldings(portfolioId)
    expect(holdings.success).toBe(true)
    expect(holdings.data?.[0]?.shares).toBe(6)
  })

  it('reports a truly invalid SELL as rejected instead of counting it as imported', async () => {
    const portfolioId = await seedEmptyPortfolio()
    const rows = [
      { type: 'SELL', symbol: 'MSFT', qty: 1, price: 300, fee: 0, timestamp: '2024-01-05T15:00:00.000Z' },
      { type: 'SELL', symbol: 'AAPL', qty: 4, price: 120, fee: 0, timestamp: '2024-01-03T15:00:00.000Z' },
      { type: 'BUY', symbol: 'AAPL', qty: 10, price: 100, fee: 1, timestamp: '2024-01-01T15:00:00.000Z' }
    ]

    const result = await importTransactions(toImportRequests(rows, portfolioId), persistViaApi)

    expect(result.created).toBe(2)
    expect(result.rejected).toHaveLength(1)
    expect(result.rejected[0]?.request.symbol).toBe('MSFT')
    expect(result.rejected[0]?.reason).toBeTruthy()
    // 거부된 행은 저장되지 않고 원장은 계속 계산 가능하다
    expect(await transactionRepository.findByPortfolioId(portfolioId)).toHaveLength(2)
    expect((await mockApiClient.getHoldings(portfolioId)).data?.[0]?.shares).toBe(6)
  })

  it('keeps file order for rows with the same timestamp', () => {
    const at = '2024-01-01T15:00:00.000Z'
    const ordered = orderForImport(
      toImportRequests(
        [
          { type: 'SELL', symbol: 'AAPL', qty: 1, price: 1, timestamp: at },
          { type: 'BUY', symbol: 'AAPL', qty: 1, price: 1, timestamp: at },
          { type: 'DEPOSIT', fee: 0, timestamp: '2023-12-31T00:00:00.000Z' }
        ],
        1
      )
    )

    expect(ordered.map((r) => r.type)).toEqual(['DEPOSIT', 'SELL', 'BUY'])
  })
})

// 워커와 같은 Papa 설정(step으로 행 수집)으로 CSV 텍스트를 파싱한다
function parseLikeWorker(csv: string) {
  const rows: CsvTransactionRow[] = []
  const malformed = new Set<number>()
  Papa.parse<CsvTransactionRow>(csv, {
    header: true,
    skipEmptyLines: true,
    transformHeader: (header) => header.trim().toLowerCase(),
    step: (results) => {
      if (results.errors.some((error) => error.type === 'Quotes')) malformed.add(rows.length)
      rows.push(results.data)
    }
  })
  return parseCsvRows(rows, malformed)
}

describe('csv row validation', () => {
  beforeEach(async () => {
    await db.users.clear()
    await db.portfolios.clear()
    await db.transactions.clear()
    await db.candles.clear()
  })

  it('returns malformed rows with row number and reason instead of dropping them', () => {
    const { valid, invalid } = parseCsvRows([
      { type: 'BUY', symbol: 'AAPL', qty: '10', price: '100', timestamp: '2024-01-01T15:00:00Z' },
      { type: 'BUY', symbol: 'AAPL', qty: '10', price: '100' },
      { type: 'TRANSFER<script>', timestamp: '2024-01-02T00:00:00Z' },
      { type: 'SELL', symbol: 'AAPL', timestamp: '2024-01-03T00:00:00Z' },
      { type: 'BUY', symbol: 'AAPL', qty: 'ten', price: '100', timestamp: '2024-01-04T00:00:00Z' },
      { type: 'DEPOSIT', fee: '0', timestamp: 'not-a-date' },
      { type: 'BUY', symbol: 'AAPL', qty: '10shares', price: '100', timestamp: '2024-01-04T00:00:00Z' }
    ])

    expect(valid).toHaveLength(1)
    expect(invalid.map((r) => r.row)).toEqual([2, 3, 4, 5, 6, 7])
    expect(invalid.every((r) => r.reason.length > 0)).toBe(true)
    // 사유에는 셀 원문을 넣지 않는다
    expect(invalid.some((r) => r.reason.includes('<script>'))).toBe(false)
  })

  it('collects rows through the worker Papa config and imports a newest-first file', async () => {
    const userId = await userRepository.create('csv2@finlab.dev', 'USER')
    const portfolioId = await portfolioRepository.create({ userId, name: 'CSV', baseCurrency: 'USD' })
    const csv = [
      'Type,Symbol,Qty,Price,Fee,Timestamp,Note',
      'SELL,AAPL,4,120,0,2024-01-03T15:00:00Z,',
      'BUY,AAPL,oops,100,0,2024-01-02T15:00:00Z,',
      'BUY,AAPL,10,100,1,2024-01-01T15:00:00Z,first'
    ].join('\n')

    const { valid, invalid } = parseLikeWorker(csv)
    expect(valid).toHaveLength(2)
    expect(invalid).toEqual([{ row: 2, reason: 'Invalid number in qty, price, or fee' }])

    const result = await importTransactions(toImportRequests(valid, portfolioId), persistViaApi)
    expect(result.created).toBe(2)
    expect(result.rejected).toEqual([])
    expect((await mockApiClient.getHoldings(portfolioId)).data?.[0]?.shares).toBe(6)

    const summary = summarizeImport(result.created, invalid, result.rejected)
    expect(summary.complete).toBe(false)
    expect(summary.message).toContain('1 invalid rows skipped')
    expect(summary.message).toContain('row 2')
  })

  it('rejects a row with broken quoting as a parse error', () => {
    const csv = ['type,symbol,qty,price,timestamp', 'BUY,"AAPL,10,100,2024-01-01T00:00:00Z'].join('\n')

    const { valid, invalid } = parseLikeWorker(csv)
    expect(valid).toEqual([])
    expect(invalid).toHaveLength(1)
    expect(invalid[0]?.reason).toContain('Malformed')
  })

  it('reports parse and persistence rejections together and only calls a clean import complete', () => {
    const rejected: RejectedImportRow[] = [
      {
        request: { portfolioId: 1, type: 'SELL', symbol: 'MSFT', qty: 1, price: 1, fee: 0, timestamp: '2024-01-05T00:00:00.000Z' },
        reason: 'Insufficient shares'
      }
    ]
    const both = summarizeImport(2, [{ row: 3, reason: 'Invalid timestamp' }], rejected)
    expect(both.complete).toBe(false)
    expect(both.message).toContain('2 transactions imported')
    expect(both.message).toContain('1 invalid rows skipped')
    expect(both.message).toContain('1 rejected')
    expect(both.message).toContain('row 3: Invalid timestamp')
    expect(both.message).toContain('MSFT')

    expect(summarizeImport(0, [{ row: 1, reason: 'Invalid timestamp' }], []).complete).toBe(false)
    expect(summarizeImport(3, [], [])).toEqual({ complete: true, message: '3 transactions imported.' })
  })
})
