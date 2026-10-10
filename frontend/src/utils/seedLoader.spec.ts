import { describe, it, beforeEach, afterEach, expect, vi } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { db } from '@/services/db/schema'
import { userRepository, portfolioRepository, transactionRepository } from '@/services/db/repositories'
import { loadAllSeedData, ensureDemoPortfolio, DEMO_EMAIL, DEMO_SEED_FLAG, DEMO_PORTFOLIO_NAME } from './seedLoader'

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
    const transactions = await transactionRepository.findByPortfolioId(result.demoPortfolioId)
    expect(transactions).toHaveLength(result.transactionsLoaded)
    expect(await portfolioRepository.findById(result.demoPortfolioId)).toMatchObject({ name: DEMO_PORTFOLIO_NAME })
    expect(await db.goals.where('portfolioId').equals(result.demoPortfolioId).count()).toBeGreaterThan(0)
    expect(await transactionRepository.findByPortfolioId(portfolioId)).toHaveLength(0)
  })

  it('repairs a stale done marker after IndexedDB data is missing', async () => {
    const userId = await userRepository.create(DEMO_EMAIL, 'USER')
    const portfolioId = await portfolioRepository.create({ userId, name: 'My Portfolio', baseCurrency: 'USD' })
    localStorage.setItem(DEMO_SEED_FLAG, 'done')

    const result = await loadAllSeedData()

    expect(result.transactionsLoaded).toBe(10)
    expect(await db.transactions.count()).toBe(10)
    expect(await portfolioRepository.findById(portfolioId)).toMatchObject({ name: 'My Portfolio' })
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

    expect(result.transactionsLoaded).toBe(10)
    expect(result.demoPortfolioId).not.toBe(portfolioId)
    const transactions = await transactionRepository.findByPortfolioId(portfolioId)
    expect(transactions).toHaveLength(1)
    expect(transactions[0]?.note).toBe('user entry')
  })

  it('is idempotent across reloads', async () => {
    const first = await loadAllSeedData()
    const second = await loadAllSeedData()

    expect(first.transactionsLoaded).toBeGreaterThan(0)
    expect(second).toEqual({ demoPortfolioId: first.demoPortfolioId, candlesLoaded: 0, transactionsLoaded: 0 })
    expect(await db.transactions.count()).toBe(first.transactionsLoaded)
  })
  it('reuses a legacy sample portfolio without adding the same fixtures again', async () => {
    const first = await loadAllSeedData()
    await portfolioRepository.update(first.demoPortfolioId, { name: 'My Portfolio' })
    const legacy = await portfolioRepository.findById(first.demoPortfolioId)
    await portfolioRepository.create({ userId: legacy!.userId, name: DEMO_PORTFOLIO_NAME, baseCurrency: 'USD' })
    localStorage.removeItem(DEMO_SEED_FLAG)

    const second = await ensureDemoPortfolio()

    expect(second.demoPortfolioId).toBe(first.demoPortfolioId)
    expect(second.transactionsLoaded).toBe(0)
    expect(await db.portfolios.count()).toBe(2)
    expect(await db.transactions.count()).toBe(10)
  })

  it('preserves existing goals and plans when repairing an empty named sample', async () => {
    const userId = await userRepository.create(DEMO_EMAIL, 'USER')
    const portfolioId = await portfolioRepository.create({ userId, name: DEMO_PORTFOLIO_NAME, baseCurrency: 'USD' })
    const goalId = await db.goals.add({ portfolioId, name: '내 목표', targetAmount: 1234, currentAmount: 123, targetDate: new Date('2030-01-01') })
    const planId = await db.plans.add({ portfolioId, symbol: 'SPY', amountPerInterval: 25, interval: 'MONTHLY', startDate: new Date('2024-01-01'), isActive: true })
    localStorage.setItem(DEMO_SEED_FLAG, 'done')

    const result = await ensureDemoPortfolio()

    expect(result.demoPortfolioId).toBe(portfolioId)
    expect(await db.goals.toArray()).toEqual([expect.objectContaining({ id: goalId, name: '내 목표', targetAmount: 1234 })])
    expect(await db.plans.toArray()).toEqual([expect.objectContaining({ id: planId, amountPerInterval: 25 })])
  })

  it('serializes concurrent recovery without duplicating portfolios, candles or transactions', async () => {
    const [first, second] = await Promise.all([ensureDemoPortfolio(), ensureDemoPortfolio()])

    expect(first.demoPortfolioId).toBe(second.demoPortfolioId)
    expect(first.transactionsLoaded + second.transactionsLoaded).toBe(10)
    expect(await db.transactions.count()).toBe(10)
    expect(await db.portfolios.count()).toBe(1)
    expect(await db.candles.count()).toBe(first.candlesLoaded + second.candlesLoaded)
    expect(await db.goals.count()).toBe(2)
    expect(await db.plans.count()).toBe(2)
  })

  it('reopens stored samples without fetching transaction fixtures or candles again', async () => {
    const first = await ensureDemoPortfolio()
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

    expect(await ensureDemoPortfolio()).toEqual({ demoPortfolioId: first.demoPortfolioId, candlesLoaded: 0, transactionsLoaded: 0 })
    expect(fetch).not.toHaveBeenCalled()
  })

})
