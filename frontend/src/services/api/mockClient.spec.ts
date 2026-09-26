import { describe, it, beforeEach, expect } from 'vitest'
import { db } from '@/services/db/schema'
import { mockApiClient } from '@/services/api'
import { transactionRepository, portfolioRepository, userRepository } from '@/services/db/repositories'

const TEST_EMAIL = 'test@finlab.dev'

async function seedPortfolio() {
  const userId = await userRepository.create(TEST_EMAIL, 'USER')
  const portfolioId = await portfolioRepository.create({ userId, name: 'Test Portfolio', baseCurrency: 'USD' })

  await db.candles.bulkAdd([
    { symbol: 'AAPL', date: '2024-01-01', open: 100, high: 110, low: 95, close: 105, volume: 1000 },
    { symbol: 'AAPL', date: '2024-01-02', open: 105, high: 112, low: 104, close: 110, volume: 900 },
    { symbol: 'AAPL', date: '2024-01-03', open: 110, high: 120, low: 109, close: 118, volume: 950 }
  ])

  await transactionRepository.create({
    portfolioId,
    type: 'BUY',
    symbol: 'AAPL',
    qty: 10,
    price: 100,
    fee: 1,
    timestamp: new Date('2024-01-01T15:00:00Z')
  })

  return { userId, portfolioId }
}

describe('mockApiClient integrations', () => {
  beforeEach(async () => {
    await db.users.clear()
    await db.portfolios.clear()
    await db.transactions.clear()
    await db.holdings.clear()
    await db.candles.clear()
    await db.goals.clear()
    await db.plans.clear()
    await db.metricSnapshots.clear()
  })

  it('calculates holdings and metrics', async () => {
    const { portfolioId } = await seedPortfolio()

    const holdingsResponse = await mockApiClient.getHoldings(portfolioId)
    expect(holdingsResponse.success).toBe(true)
    expect(holdingsResponse.data).toHaveLength(1)
    expect(holdingsResponse.data?.[0]?.symbol).toBe('AAPL')

    const metricsResponse = await mockApiClient.getPortfolioMetrics(portfolioId)
    expect(metricsResponse.success).toBe(true)
    expect(metricsResponse.data?.totalValue).toBeGreaterThan(0)
  })

  it('rejects a SELL that exceeds held shares so holdings stay computable', async () => {
    const { portfolioId } = await seedPortfolio()

    const oversell = await mockApiClient.createTransaction({
      portfolioId,
      type: 'SELL',
      symbol: 'AAPL',
      qty: 15,
      price: 120,
      fee: 0,
      timestamp: new Date('2024-01-03T15:00:00Z').toISOString()
    })
    expect(oversell.success).toBe(false)
    expect(oversell.error?.code).toBe('VALIDATION_ERROR')

    const unheld = await mockApiClient.createTransaction({
      portfolioId,
      type: 'SELL',
      symbol: 'MSFT',
      qty: 1,
      price: 300,
      fee: 0,
      timestamp: new Date('2024-01-03T15:00:00Z').toISOString()
    })
    expect(unheld.success).toBe(false)

    // 매수보다 이른 시점의 매도도 FIFO 순서상 보유 수량이 없으므로 거부된다
    const backdated = await mockApiClient.createTransaction({
      portfolioId,
      type: 'SELL',
      symbol: 'AAPL',
      qty: 1,
      price: 90,
      fee: 0,
      timestamp: new Date('2023-12-31T15:00:00Z').toISOString()
    })
    expect(backdated.success).toBe(false)

    const holdings = await mockApiClient.getHoldings(portfolioId)
    expect(holdings.success).toBe(true)
    expect(holdings.data?.[0]?.shares).toBe(10)

    const partial = await mockApiClient.createTransaction({
      portfolioId,
      type: 'SELL',
      symbol: 'AAPL',
      qty: 4,
      price: 120,
      fee: 0,
      timestamp: new Date('2024-01-03T15:00:00Z').toISOString()
    })
    expect(partial.success).toBe(true)
    expect((await mockApiClient.getHoldings(portfolioId)).data?.[0]?.shares).toBe(6)
  })

  it('rejects deleting a BUY that a later SELL depends on and keeps the stored rows', async () => {
    const { portfolioId } = await seedPortfolio()
    const [buy] = await transactionRepository.findByPortfolioId(portfolioId)

    const sell = await mockApiClient.createTransaction({
      portfolioId,
      type: 'SELL',
      symbol: 'AAPL',
      qty: 4,
      price: 120,
      fee: 0,
      timestamp: new Date('2024-01-03T15:00:00Z').toISOString()
    })
    expect(sell.success).toBe(true)

    const rejected = await mockApiClient.deleteTransaction(buy!.id!)
    expect(rejected.success).toBe(false)
    expect(rejected.error?.code).toBe('VALIDATION_ERROR')

    // 실패 시 저장된 거래는 그대로이고 holdings 계산도 계속 가능하다
    expect(await transactionRepository.findById(buy!.id!)).toBeDefined()
    expect(await transactionRepository.findByPortfolioId(portfolioId)).toHaveLength(2)
    const holdings = await mockApiClient.getHoldings(portfolioId)
    expect(holdings.success).toBe(true)
    expect(holdings.data?.[0]?.shares).toBe(6)
    expect((await mockApiClient.getPortfolioMetrics(portfolioId)).success).toBe(true)

    // 의존하는 매도를 먼저 지우면 매수도 삭제할 수 있다
    expect((await mockApiClient.deleteTransaction(sell.data!.id)).success).toBe(true)
    expect((await mockApiClient.deleteTransaction(buy!.id!)).success).toBe(true)
    expect((await mockApiClient.getHoldings(portfolioId)).data).toEqual([])
  })

  it('allows deleting a BUY when another BUY still covers the later SELL', async () => {
    const { portfolioId } = await seedPortfolio()
    const [firstBuy] = await transactionRepository.findByPortfolioId(portfolioId)

    await mockApiClient.createTransaction({
      portfolioId,
      type: 'BUY',
      symbol: 'AAPL',
      qty: 5,
      price: 105,
      fee: 0,
      timestamp: new Date('2024-01-02T15:00:00Z').toISOString()
    })
    await mockApiClient.createTransaction({
      portfolioId,
      type: 'SELL',
      symbol: 'AAPL',
      qty: 4,
      price: 120,
      fee: 0,
      timestamp: new Date('2024-01-03T15:00:00Z').toISOString()
    })

    const deleted = await mockApiClient.deleteTransaction(firstBuy!.id!)
    expect(deleted.success).toBe(true)
    expect((await mockApiClient.getHoldings(portfolioId)).data?.[0]?.shares).toBe(1)
  })

  it('still lets users delete rows from a ledger that was already invalid before validation existed', async () => {
    const { portfolioId } = await seedPortfolio()
    // 검증 도입 전에 저장된 초과 매도 두 건 (API 우회)
    const firstBad = await transactionRepository.create({
      portfolioId,
      type: 'SELL',
      symbol: 'AAPL',
      qty: 15,
      price: 120,
      fee: 0,
      timestamp: new Date('2024-01-03T15:00:00Z')
    })
    const secondBad = await transactionRepository.create({
      portfolioId,
      type: 'SELL',
      symbol: 'MSFT',
      qty: 1,
      price: 300,
      fee: 0,
      timestamp: new Date('2024-01-03T16:00:00Z')
    })
    expect((await mockApiClient.getHoldings(portfolioId)).success).toBe(false)

    expect((await mockApiClient.deleteTransaction(firstBad)).success).toBe(true)
    expect((await mockApiClient.deleteTransaction(secondBad)).success).toBe(true)
    expect((await mockApiClient.getHoldings(portfolioId)).data?.[0]?.shares).toBe(10)
  })

  it('creates and lists goals/plans', async () => {
    const { portfolioId } = await seedPortfolio()

    const goalResult = await mockApiClient.createGoal({
      portfolioId,
      name: 'Retirement',
      targetAmount: 200000,
      targetDate: new Date('2045-12-31').toISOString(),
      currentAmount: 10000
    })
    expect(goalResult.success).toBe(true)

    const goals = await mockApiClient.getGoals(portfolioId)
    expect(goals.success).toBe(true)
    expect(goals.data).toHaveLength(1)

    const planResult = await mockApiClient.createPlan({
      portfolioId,
      symbol: 'AAPL',
      amountPerInterval: 500,
      interval: 'MONTHLY',
      startDate: new Date('2024-01-01').toISOString(),
      isActive: true
    })
    expect(planResult.success).toBe(true)

    const plans = await mockApiClient.getPlans(portfolioId)
    expect(plans.success).toBe(true)
    expect(plans.data).toHaveLength(1)
  })
})
