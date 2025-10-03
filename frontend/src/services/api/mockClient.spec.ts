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
