import { describe, it, beforeEach, afterEach, expect, vi } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { db } from '@/services/db/schema'
import { userRepository, portfolioRepository, transactionRepository } from '@/services/db/repositories'
import { loadAllSeedData, DEMO_EMAIL, DEMO_SEED_FLAG } from './seedLoader'

// vitest runs from frontend/, where the seed files are served as static assets
const publicDir = resolve(process.cwd(), 'public')

async function clearDb() {
  await Promise.all([
    db.users.clear(),
    db.portfolios.clear(),
    db.transactions.clear(),
    db.holdings.clear(),
    db.candles.clear(),
    db.goals.clear(),
    db.plans.clear(),
    db.metricSnapshots.clear()
  ])
}

describe('seedLoader', () => {
  beforeEach(async () => {
    await clearDb()
    localStorage.removeItem(DEMO_SEED_FLAG)
    vi.stubGlobal('fetch', async (path: string) => {
      const body = await readFile(`${publicDir}${path}`, 'utf8')
      return new Response(body)
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('seeds the demo login account even when schema.populate already created it', async () => {
    // schema.populate()와 동일한 상태: 데모 사용자 + 빈 포트폴리오
    const userId = await userRepository.create(DEMO_EMAIL, 'USER')
    const portfolioId = await portfolioRepository.create({ userId, name: 'My Portfolio', baseCurrency: 'USD' })

    const result = await loadAllSeedData()

    expect(result.candlesLoaded).toBeGreaterThan(100)
    expect(result.transactionsLoaded).toBeGreaterThan(0)
    const transactions = await transactionRepository.findByPortfolioId(portfolioId)
    expect(transactions).toHaveLength(result.transactionsLoaded)
    expect(await db.goals.where('portfolioId').equals(portfolioId).count()).toBeGreaterThan(0)
  })

  it('never overwrites transactions the user already entered', async () => {
    const userId = await userRepository.create(DEMO_EMAIL, 'USER')
    const portfolioId = await portfolioRepository.create({ userId, name: 'My Portfolio', baseCurrency: 'USD' })
    await transactionRepository.create({
      portfolioId,
      type: 'DEPOSIT',
      fee: 0,
      timestamp: new Date('2024-05-01T00:00:00Z'),
      note: 'user entry'
    })

    const result = await loadAllSeedData()

    expect(result.transactionsLoaded).toBe(0)
    const transactions = await transactionRepository.findByPortfolioId(portfolioId)
    expect(transactions).toHaveLength(1)
    expect(transactions[0]?.note).toBe('user entry')
  })

  it('is idempotent across reloads', async () => {
    const first = await loadAllSeedData()
    const second = await loadAllSeedData()

    expect(first.transactionsLoaded).toBeGreaterThan(0)
    expect(second).toEqual({ candlesLoaded: 0, transactionsLoaded: 0 })
    expect(await db.transactions.count()).toBe(first.transactionsLoaded)
  })
})
