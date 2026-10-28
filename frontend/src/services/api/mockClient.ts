/**
 * Mock API Client
 * 백엔드 없이 동작하는 mock API (IndexedDB 기반)
 */

import { z } from 'zod'
import Decimal from 'decimal.js'
import { db } from '@/services/db/schema'
import {
  userRepository,
  portfolioRepository,
  transactionRepository,
  goalRepository,
  planRepository,
  metricSnapshotRepository
} from '@/services/db/repositories'
import { calculateHoldingsFromTransactions, type Holding as FifoHolding } from '@/utils/fifoCalculator'
import * as schemas from './schemas'
import { calculateMetrics } from '@/utils/metrics'

/**
 * API 응답 래퍼
 */
export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: schemas.ApiError
}

/**
 * Mock 인증 토큰 생성
 */
function generateMockToken(userId: number): string {
  return `mock_token_${userId}_${Date.now()}`
}

/**
 * Date를 ISO string으로 변환
 */
function toISOString(date: Date): string {
  return date.toISOString()
}

async function getSortedCandles(symbol: string) {
  return await db.candles.where('symbol').equals(symbol).sortBy('date')
}

/**
 * FIFO 원장 검증: 계산이 가능하면 null, 아니면 오류 메시지를 반환한다.
 * 잘못된 원장이 저장되면 이후 holdings / analytics 계산이 계속 실패하므로 저장 전에 확인한다.
 */
function findLedgerError(transactions: Parameters<typeof calculateHoldingsFromTransactions>[0]): string | null {
  try {
    calculateHoldingsFromTransactions(transactions)
    return null
  } catch (holdingError) {
    return holdingError instanceof Error ? holdingError.message : '매도 수량이 보유 수량을 초과합니다'
  }
}

async function computeHoldingsData(portfolioId: number) {
  const transactions = await transactionRepository.findByPortfolioId(portfolioId)

  if (transactions.length === 0) {
    return {
      holdings: [] as schemas.HoldingResponse[],
      holdingsMap: new Map<string, FifoHolding>(),
      candlesBySymbol: new Map<string, Awaited<ReturnType<typeof getSortedCandles>>>(),
      totalValue: 0,
      transactions
    }
  }

  const holdingsMap = calculateHoldingsFromTransactions(transactions)
  const candlesBySymbol = new Map<string, Awaited<ReturnType<typeof getSortedCandles>>>()
  const holdings: schemas.HoldingResponse[] = []
  let totalValue = new Decimal(0)
  let idCounter = 1

  for (const [symbol, holding] of holdingsMap.entries()) {
    const candles = await getSortedCandles(symbol)
    candlesBySymbol.set(symbol, candles)
    const latestCandle = candles.at(-1)
    const latestPrice = latestCandle?.close ?? holding.avgCost
    const marketValue = new Decimal(holding.totalShares).times(latestPrice)
    const costBasis = new Decimal(holding.totalShares).times(holding.avgCost)

    holdings.push({
      id: idCounter++,
      portfolioId,
      symbol,
      shares: holding.totalShares,
      avgCost: holding.avgCost,
      marketValue: marketValue.toNumber(),
      unrealizedPnL: marketValue.minus(costBasis).toNumber(),
      lastUpdated: new Date().toISOString()
    })

    totalValue = totalValue.plus(marketValue)
  }

  return {
    holdings,
    holdingsMap,
    candlesBySymbol,
    totalValue: totalValue.toNumber(),
    transactions
  }
}

function computePortfolioMetrics(
  holdings: schemas.HoldingResponse[],
  candlesBySymbol: Map<string, Awaited<ReturnType<typeof getSortedCandles>>>,
  totalValue: number
) {
  if (holdings.length === 0 || totalValue === 0) {
    return {
      returns: [] as number[],
      snapshot: {
        totalValue: 0,
        cash: 0,
        pnlDaily: 0,
        pnlYTD: 0,
        volatility: 0,
        sharpe: 0,
        maxDrawdown: 0
      }
    }
  }

  const weightBySymbol = new Map<string, number>()
  holdings.forEach((holding) => {
    const weight = holding.marketValue / totalValue
    weightBySymbol.set(holding.symbol, weight)
  })

  const returnByDate = new Map<string, number>()

  for (const holding of holdings) {
    const candles = candlesBySymbol.get(holding.symbol) ?? []
    const weight = weightBySymbol.get(holding.symbol) ?? 0

    for (let i = 1; i < candles.length; i++) {
      const prev = candles[i - 1]
      const curr = candles[i]
      if (!prev || !curr || prev.close === 0) continue

      const dailyReturn = ((curr.close - prev.close) / prev.close) * 100
      const aggregated = returnByDate.get(curr.date) ?? 0
      returnByDate.set(curr.date, aggregated + dailyReturn * weight)
    }
  }

  const sortedDates = Array.from(returnByDate.keys()).sort()
  const returns = sortedDates.map((date) => returnByDate.get(date) ?? 0)
  // No price history keeps the existing zero snapshot; the worker rejects empty input.
  const { volatility, sharpeRatio: sharpe, maxDrawdown } = returns.length
    ? calculateMetrics({ returns })
    : { volatility: 0, sharpeRatio: 0, maxDrawdown: 0 }
  const pnlDaily = returns.at(-1) ? (returns.at(-1)! / 100) * totalValue : 0
  const cumulativeReturn = returns.reduce(
    (acc, ret) => acc.times(new Decimal(1).plus(ret / 100)),
    new Decimal(1)
  )
  const pnlYTD = cumulativeReturn.minus(1).times(totalValue).toNumber()

  return {
    returns,
    snapshot: {
      totalValue,
      cash: 0,
      pnlDaily,
      pnlYTD,
      volatility,
      sharpe,
      maxDrawdown
    }
  }
}

/**
 * Mock API Client Class
 */
export class MockApiClient {
  private currentUserId: number | null = null

  // ========== Auth Methods ==========

  /**
   * 로그인 (Mock 전용, 비밀번호는 검증 후 저장하지 않음)
   */
  async login(request: schemas.LoginRequest | Pick<schemas.LoginRequest, 'email'>): Promise<ApiResponse<schemas.LoginResponse>> {
    try {
      // 공개 데모의 기존 이메일 전용 예제는 유지한다. 전달된 비밀번호는 항상 검증하며 저장하지 않는다.
      const validated = request?.email === 'demo@finlab.app' && !('password' in request)
        ? schemas.LoginRequestSchema.omit({ password: true }).parse(request)
        : schemas.LoginRequestSchema.parse(request)

      // 사용자 조회 (없으면 생성)
      let user = await userRepository.findByEmail(validated.email)
      if (!user) {
        const userId = await userRepository.create(validated.email, 'USER')
        user = await userRepository.findById(userId)
      }

      if (!user) {
        return {
          success: false,
          error: {
            code: 'USER_NOT_FOUND',
            message: '사용자를 찾을 수 없습니다'
          }
        }
      }

      this.currentUserId = user.id!

      const response: schemas.LoginResponse = {
        userId: user.id!,
        email: user.email,
        role: user.role,
        token: generateMockToken(user.id!)
      }

      return {
        success: true,
        data: schemas.LoginResponseSchema.parse(response)
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        return {
          success: false,
          error: {
            code: 'VALIDATION_ERROR',
            message: '입력 데이터가 유효하지 않습니다',
            details: error.flatten()
          }
        }
      }
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '알 수 없는 오류'
        }
      }
    }
  }

  /**
   * 현재 사용자 ID 조회
   */
  getCurrentUserId(): number | null {
    return this.currentUserId
  }

  // ========== Portfolio Methods ==========

  /**
   * 포트폴리오 목록 조회
   */
  async getPortfolios(userId: number): Promise<ApiResponse<schemas.PortfolioResponse[]>> {
    try {
      const portfolios = await portfolioRepository.findByUserId(userId)

      const response = portfolios.map((p) => ({
        id: p.id!,
        userId: p.userId,
        name: p.name,
        baseCurrency: p.baseCurrency,
        createdAt: toISOString(p.createdAt),
        updatedAt: toISOString(p.updatedAt)
      }))

      return {
        success: true,
        data: z.array(schemas.PortfolioResponseSchema).parse(response)
      }
    } catch (error) {
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '알 수 없는 오류'
        }
      }
    }
  }

  /**
   * 포트폴리오 단건 조회
   */
  async getPortfolio(id: number): Promise<ApiResponse<schemas.PortfolioResponse>> {
    try {
      const portfolio = await portfolioRepository.findById(id)

      if (!portfolio) {
        return {
          success: false,
          error: {
            code: 'NOT_FOUND',
            message: '포트폴리오를 찾을 수 없습니다'
          }
        }
      }

      const response: schemas.PortfolioResponse = {
        id: portfolio.id!,
        userId: portfolio.userId,
        name: portfolio.name,
        baseCurrency: portfolio.baseCurrency,
        createdAt: toISOString(portfolio.createdAt),
        updatedAt: toISOString(portfolio.updatedAt)
      }

      return {
        success: true,
        data: schemas.PortfolioResponseSchema.parse(response)
      }
    } catch (error) {
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '알 수 없는 오류'
        }
      }
    }
  }

  /**
   * 포트폴리오 생성
   */
  async createPortfolio(
    userId: number,
    request: schemas.CreatePortfolioRequest
  ): Promise<ApiResponse<schemas.PortfolioResponse>> {
    try {
      const validated = schemas.CreatePortfolioRequestSchema.parse(request)

      const portfolioId = await portfolioRepository.create({
        userId,
        name: validated.name,
        baseCurrency: validated.baseCurrency
      })

      const portfolio = await portfolioRepository.findById(portfolioId)

      if (!portfolio) {
        return {
          success: false,
          error: {
            code: 'CREATION_FAILED',
            message: '포트폴리오 생성에 실패했습니다'
          }
        }
      }

      const response: schemas.PortfolioResponse = {
        id: portfolio.id!,
        userId: portfolio.userId,
        name: portfolio.name,
        baseCurrency: portfolio.baseCurrency,
        createdAt: toISOString(portfolio.createdAt),
        updatedAt: toISOString(portfolio.updatedAt)
      }

      return {
        success: true,
        data: schemas.PortfolioResponseSchema.parse(response)
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        return {
          success: false,
          error: {
            code: 'VALIDATION_ERROR',
            message: '입력 데이터가 유효하지 않습니다',
            details: error.flatten()
          }
        }
      }
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '알 수 없는 오류'
        }
      }
    }
  }

  /**
   * 포트폴리오 업데이트
   */
  async updatePortfolio(
    id: number,
    request: schemas.UpdatePortfolioRequest
  ): Promise<ApiResponse<schemas.PortfolioResponse>> {
    try {
      const validated = schemas.UpdatePortfolioRequestSchema.parse(request)

      await portfolioRepository.update(id, validated)

      const portfolio = await portfolioRepository.findById(id)

      if (!portfolio) {
        return {
          success: false,
          error: {
            code: 'NOT_FOUND',
            message: '포트폴리오를 찾을 수 없습니다'
          }
        }
      }

      const response: schemas.PortfolioResponse = {
        id: portfolio.id!,
        userId: portfolio.userId,
        name: portfolio.name,
        baseCurrency: portfolio.baseCurrency,
        createdAt: toISOString(portfolio.createdAt),
        updatedAt: toISOString(portfolio.updatedAt)
      }

      return {
        success: true,
        data: schemas.PortfolioResponseSchema.parse(response)
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        return {
          success: false,
          error: {
            code: 'VALIDATION_ERROR',
            message: '입력 데이터가 유효하지 않습니다',
            details: error.flatten()
          }
        }
      }
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '알 수 없는 오류'
        }
      }
    }
  }

  /**
   * 포트폴리오 삭제
   */
  async deletePortfolio(id: number): Promise<ApiResponse<void>> {
    try {
      await portfolioRepository.delete(id)

      return {
        success: true
      }
    } catch (error) {
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '알 수 없는 오류'
        }
      }
    }
  }

  // ========== Transaction Methods ==========

  /**
   * 거래 목록 조회
   */
  async getTransactions(
    portfolioId: number
  ): Promise<ApiResponse<schemas.TransactionResponse[]>> {
    try {
      const transactions = await transactionRepository.findByPortfolioId(portfolioId)

      const response = transactions.map((t) => ({
        id: t.id!,
        portfolioId: t.portfolioId,
        type: t.type,
        symbol: t.symbol,
        qty: t.qty,
        price: t.price,
        fee: t.fee,
        timestamp: toISOString(t.timestamp),
        note: t.note
      }))

      return {
        success: true,
        data: z.array(schemas.TransactionResponseSchema).parse(response)
      }
    } catch (error) {
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '알 수 없는 오류'
        }
      }
    }
  }

  /**
   * 거래 생성
   */
  async createTransaction(
    request: schemas.CreateTransactionRequest
  ): Promise<ApiResponse<schemas.TransactionResponse>> {
    try {
      const validated = schemas.CreateTransactionRequestSchema.parse(request)

      // 매도는 FIFO 기준 보유 수량 이내여야 한다. 초과 매도가 저장되면 이후 holdings 계산이 계속 실패한다.
      if (validated.type === 'SELL') {
        const existing = await transactionRepository.findByPortfolioId(validated.portfolioId)
        const ledgerError = findLedgerError([
          ...existing,
          {
            portfolioId: validated.portfolioId,
            type: validated.type,
            symbol: validated.symbol,
            qty: validated.qty,
            price: validated.price,
            fee: validated.fee,
            timestamp: new Date(validated.timestamp)
          }
        ])
        if (ledgerError) {
          return {
            success: false,
            error: { code: 'VALIDATION_ERROR', message: ledgerError }
          }
        }
      }

      const transactionId = await transactionRepository.create({
        portfolioId: validated.portfolioId,
        type: validated.type,
        symbol: validated.symbol,
        qty: validated.qty,
        price: validated.price,
        fee: validated.fee,
        timestamp: new Date(validated.timestamp),
        note: validated.note
      })

      const transaction = await transactionRepository.findById(transactionId)

      if (!transaction) {
        return {
          success: false,
          error: {
            code: 'CREATION_FAILED',
            message: '거래 생성에 실패했습니다'
          }
        }
      }

      const response: schemas.TransactionResponse = {
        id: transaction.id!,
        portfolioId: transaction.portfolioId,
        type: transaction.type,
        symbol: transaction.symbol,
        qty: transaction.qty,
        price: transaction.price,
        fee: transaction.fee,
        timestamp: toISOString(transaction.timestamp),
        note: transaction.note
      }

      return {
        success: true,
        data: schemas.TransactionResponseSchema.parse(response)
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        return {
          success: false,
          error: {
            code: 'VALIDATION_ERROR',
            message: '입력 데이터가 유효하지 않습니다',
            details: error.flatten()
          }
        }
      }
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '알 수 없는 오류'
        }
      }
    }
  }

  /**
   * 거래 삭제
   */
  async deleteTransaction(id: number): Promise<ApiResponse<void>> {
    try {
      // 이후 매도가 의존하는 매수를 지우면 남은 원장의 holdings 계산이 실패한다. 연쇄 삭제 대신 거부한다.
      // 이미 깨진 원장(검증 도입 전 데이터)은 복구를 위해 삭제를 막지 않는다.
      const target = await transactionRepository.findById(id)
      if (target) {
        const ledger = await transactionRepository.findByPortfolioId(target.portfolioId)
        const remainingError = findLedgerError(ledger.filter((t) => t.id !== id))
        if (remainingError && !findLedgerError(ledger)) {
          return {
            success: false,
            error: {
              code: 'VALIDATION_ERROR',
              message: `이 거래에 의존하는 이후 매도가 있어 삭제할 수 없습니다 (${remainingError})`
            }
          }
        }
      }

      await transactionRepository.delete(id)

      return {
        success: true
      }
    } catch (error) {
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '알 수 없는 오류'
        }
      }
    }
  }

  // ========== Holdings & Metrics ==========

  async getHoldings(portfolioId: number): Promise<ApiResponse<schemas.HoldingResponse[]>> {
    try {
      const { holdings } = await computeHoldingsData(portfolioId)

      return {
        success: true,
        data: z.array(schemas.HoldingResponseSchema).parse(holdings)
      }
    } catch (error) {
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : 'Holdings 계산에 실패했습니다'
        }
      }
    }
  }

  async getPortfolioMetrics(portfolioId: number): Promise<ApiResponse<schemas.MetricSnapshotResponse>> {
    try {
      const { holdings, candlesBySymbol, totalValue } = await computeHoldingsData(portfolioId)
      const { snapshot } = computePortfolioMetrics(holdings, candlesBySymbol, totalValue)

      const asOf = new Date()

      await metricSnapshotRepository.clearForPortfolio(portfolioId)
      await metricSnapshotRepository.create({
        portfolioId,
        asOf,
        totalValue: snapshot.totalValue,
        cash: snapshot.cash,
        pnlDaily: snapshot.pnlDaily,
        pnlYTD: snapshot.pnlYTD,
        volatility: snapshot.volatility,
        sharpe: snapshot.sharpe,
        maxDrawdown: snapshot.maxDrawdown
      })

      const response: schemas.MetricSnapshotResponse = {
        id: 0,
        portfolioId,
        asOf: toISOString(asOf),
        totalValue: snapshot.totalValue,
        cash: snapshot.cash,
        pnlDaily: snapshot.pnlDaily,
        pnlYTD: snapshot.pnlYTD,
        volatility: snapshot.volatility,
        sharpe: snapshot.sharpe,
        maxDrawdown: snapshot.maxDrawdown
      }

      return {
        success: true,
        data: schemas.MetricSnapshotResponseSchema.parse(response)
      }
    } catch (error) {
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '포트폴리오 지표 계산에 실패했습니다'
        }
      }
    }
  }

  // ========== Goals ==========

  async getGoals(portfolioId: number): Promise<ApiResponse<schemas.GoalResponse[]>> {
    try {
      const goals = await goalRepository.findByPortfolioId(portfolioId)

      const response = goals.map((goal) => ({
        id: goal.id!,
        portfolioId: goal.portfolioId,
        name: goal.name,
        targetAmount: goal.targetAmount,
        targetDate: toISOString(goal.targetDate),
        currentAmount: goal.currentAmount
      }))

      return {
        success: true,
        data: z.array(schemas.GoalResponseSchema).parse(response)
      }
    } catch (error) {
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '목표 데이터를 불러오지 못했습니다'
        }
      }
    }
  }

  async createGoal(request: schemas.CreateGoalRequest): Promise<ApiResponse<schemas.GoalResponse>> {
    try {
      const validated = schemas.CreateGoalRequestSchema.parse(request)

      const id = await goalRepository.create({
        portfolioId: validated.portfolioId,
        name: validated.name,
        targetAmount: validated.targetAmount,
        targetDate: new Date(validated.targetDate),
        currentAmount: validated.currentAmount
      })

      const created = await goalRepository.findByPortfolioId(validated.portfolioId)
      const goal = created.find((item) => item.id === id)

      if (!goal) {
        return {
          success: false,
          error: {
            code: 'CREATION_FAILED',
            message: '목표 생성에 실패했습니다'
          }
        }
      }

      const response: schemas.GoalResponse = {
        id: goal.id!,
        portfolioId: goal.portfolioId,
        name: goal.name,
        targetAmount: goal.targetAmount,
        targetDate: toISOString(goal.targetDate),
        currentAmount: goal.currentAmount
      }

      return {
        success: true,
        data: schemas.GoalResponseSchema.parse(response)
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        return {
          success: false,
          error: {
            code: 'VALIDATION_ERROR',
            message: '목표 데이터가 유효하지 않습니다',
            details: error.flatten()
          }
        }
      }

      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '목표 생성 중 오류가 발생했습니다'
        }
      }
    }
  }

  async deleteGoal(id: number): Promise<ApiResponse<void>> {
    try {
      await goalRepository.delete(id)
      return { success: true }
    } catch (error) {
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '목표 삭제에 실패했습니다'
        }
      }
    }
  }

  // ========== Plans ==========

  async getPlans(portfolioId: number): Promise<ApiResponse<schemas.PlanResponse[]>> {
    try {
      const plans = await planRepository.findByPortfolioId(portfolioId)

      const response = plans.map((plan) => ({
        id: plan.id!,
        portfolioId: plan.portfolioId,
        symbol: plan.symbol,
        amountPerInterval: plan.amountPerInterval,
        interval: plan.interval,
        startDate: toISOString(plan.startDate),
        endDate: plan.endDate ? toISOString(plan.endDate) : undefined,
        isActive: plan.isActive
      }))

      return {
        success: true,
        data: z.array(schemas.PlanResponseSchema).parse(response)
      }
    } catch (error) {
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '투자 계획을 불러오지 못했습니다'
        }
      }
    }
  }

  async createPlan(request: schemas.CreatePlanRequest): Promise<ApiResponse<schemas.PlanResponse>> {
    try {
      const validated = schemas.CreatePlanRequestSchema.parse(request)

      const id = await planRepository.create({
        portfolioId: validated.portfolioId,
        symbol: validated.symbol,
        amountPerInterval: validated.amountPerInterval,
        interval: validated.interval,
        startDate: new Date(validated.startDate),
        endDate: validated.endDate ? new Date(validated.endDate) : undefined,
        isActive: validated.isActive ?? true
      })

      const plans = await planRepository.findByPortfolioId(validated.portfolioId)
      const plan = plans.find((item) => item.id === id)

      if (!plan) {
        return {
          success: false,
          error: {
            code: 'CREATION_FAILED',
            message: '투자 계획 생성에 실패했습니다'
          }
        }
      }

      const response: schemas.PlanResponse = {
        id: plan.id!,
        portfolioId: plan.portfolioId,
        symbol: plan.symbol,
        amountPerInterval: plan.amountPerInterval,
        interval: plan.interval,
        startDate: toISOString(plan.startDate),
        endDate: plan.endDate ? toISOString(plan.endDate) : undefined,
        isActive: plan.isActive
      }

      return {
        success: true,
        data: schemas.PlanResponseSchema.parse(response)
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        return {
          success: false,
          error: {
            code: 'VALIDATION_ERROR',
            message: '투자 계획 데이터가 유효하지 않습니다',
            details: error.flatten()
          }
        }
      }

      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '투자 계획 생성 중 오류가 발생했습니다'
        }
      }
    }
  }

  async deletePlan(id: number): Promise<ApiResponse<void>> {
    try {
      await planRepository.delete(id)
      return { success: true }
    } catch (error) {
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '투자 계획 삭제에 실패했습니다'
        }
      }
    }
  }

  // ========== Market Data Methods ==========

  /**
   * OHLCV 데이터 조회
   */
  async getMarketData(
    query: schemas.MarketDataQuery
  ): Promise<ApiResponse<schemas.CandleResponse[]>> {
    try {
      const validated = schemas.MarketDataQuerySchema.parse(query)

      let candles = await db.candles.where('symbol').equals(validated.symbol).toArray()

      // 날짜 필터링
      if (validated.startDate || validated.endDate) {
        candles = candles.filter((c) => {
          if (validated.startDate && c.date < validated.startDate) return false
          if (validated.endDate && c.date > validated.endDate) return false
          return true
        })
      }

      const response = candles.map((c) => ({
        symbol: c.symbol,
        date: c.date,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
        volume: c.volume
      }))

      return {
        success: true,
        data: z.array(schemas.CandleResponseSchema).parse(response)
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        return {
          success: false,
          error: {
            code: 'VALIDATION_ERROR',
            message: '입력 데이터가 유효하지 않습니다',
            details: error.flatten()
          }
        }
      }
      return {
        success: false,
        error: {
          code: 'INTERNAL_ERROR',
          message: error instanceof Error ? error.message : '알 수 없는 오류'
        }
      }
    }
  }
}

// Singleton export
export const mockApiClient = new MockApiClient()
