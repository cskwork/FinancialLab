/**
 * Seed Data Loader
 * 데모 데이터를 IndexedDB에 로드하는 유틸리티
 */

import Papa from 'papaparse'
import { db, type Transaction, type Candle } from '@/services/db/schema'
import { userRepository } from '@/services/db/repositories/UserRepository'
import { portfolioRepository } from '@/services/db/repositories/PortfolioRepository'
import { goalRepository } from '@/services/db/repositories/GoalRepository'
import { planRepository } from '@/services/db/repositories/PlanRepository'
import { logger } from './logger'

/**
 * OHLCV CSV 데이터 타입
 */
interface OHLCVRow {
  date: string
  open: string
  high: string
  low: string
  close: string
  volume: string
}

/**
 * Transaction JSON 데이터 타입
 */
interface TransactionSeed {
  type: 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAW'
  symbol?: string
  qty?: number
  price?: number
  fee: number
  timestamp: string
  note?: string
}

interface SeedData {
  transactions: TransactionSeed[]
}

/**
 * CSV 파일을 파싱하여 Candle 데이터로 변환
 */
async function loadOHLCVData(symbol: string, csvPath: string): Promise<Candle[]> {
  const response = await fetch(csvPath)
  const csvText = await response.text()

  return new Promise((resolve, reject) => {
    Papa.parse<OHLCVRow>(csvText, {
      header: true,
      skipEmptyLines: true,
      complete: (results) => {
        const candles: Candle[] = results.data.map((row) => ({
          symbol,
          date: row.date, // YYYY-MM-DD 형식 문자열
          open: parseFloat(row.open),
          high: parseFloat(row.high),
          low: parseFloat(row.low),
          close: parseFloat(row.close),
          volume: parseInt(row.volume, 10)
        }))
        resolve(candles)
      },
      error: (error: Error) => {
        reject(new Error(`Failed to parse ${csvPath}: ${error.message}`))
      }
    })
  })
}

/**
 * JSON 파일에서 거래 데이터 로드
 */
async function loadTransactionSeeds(jsonPath: string): Promise<TransactionSeed[]> {
  const response = await fetch(jsonPath)
  const data: SeedData = await response.json()
  return data.transactions
}

/**
 * 데모 사용자 및 포트폴리오 생성
 */
async function createDemoUserAndPortfolio(): Promise<{ userId: number; portfolioId: number }> {
  // 기존 데모 사용자 확인
  const existingUser = await userRepository.findByEmail('demo@finlab.dev')

  let userId: number
  if (existingUser) {
    userId = existingUser.id!
  } else {
    userId = await userRepository.create('demo@finlab.dev', 'USER')
  }

  // 기존 포트폴리오 확인
  const existingPortfolios = await portfolioRepository.findByUserId(userId)
  const firstPortfolio = existingPortfolios[0]

  let portfolioId: number
  if (firstPortfolio && firstPortfolio.id !== undefined) {
    portfolioId = firstPortfolio.id
  } else {
    portfolioId = await portfolioRepository.create({
      userId,
      name: 'Demo Portfolio',
      baseCurrency: 'USD'
    })
  }

  return { userId, portfolioId }
}

/**
 * 전체 seed 데이터 로드
 */
export async function loadAllSeedData(): Promise<{
  candlesLoaded: number
  transactionsLoaded: number
}> {
  try {
    // 1. 사용자 및 포트폴리오 생성
    const { portfolioId } = await createDemoUserAndPortfolio()

    // 2. OHLCV 데이터 로드
    const [aaplCandles, spyCandles] = await Promise.all([
      loadOHLCVData('AAPL', '/data/ohlcv/AAPL.csv'),
      loadOHLCVData('SPY', '/data/ohlcv/SPY.csv')
    ])

    const allCandles = [...aaplCandles, ...spyCandles]

    // 기존 캔들 데이터 삭제 후 재로드
    await db.candles.clear()
    await db.candles.bulkAdd(allCandles)

    // 3. 거래 데이터 로드
    const transactionSeeds = await loadTransactionSeeds('/data/seeds/sample-transactions.json')

    const transactions: Omit<Transaction, 'id'>[] = transactionSeeds.map((seed) => ({
      portfolioId,
      type: seed.type,
      symbol: seed.symbol,
      qty: seed.qty,
      price: seed.price,
      fee: seed.fee,
      timestamp: new Date(seed.timestamp),
      note: seed.note
    }))

    // 기존 거래 데이터 삭제 후 재로드
    await db.transactions.where('portfolioId').equals(portfolioId).delete()
    await db.transactions.bulkAdd(transactions)

    // 4. Goal & Plan 샘플 데이터 로드
    await seedGoalsAndPlans(portfolioId)

    return {
      candlesLoaded: allCandles.length,
      transactionsLoaded: transactions.length
    }
  } catch (error) {
    logger.error('Failed to load seed data', error)
    throw error
  }
}

/**
 * 데이터베이스가 비어있는지 확인
 */
export async function isDatabaseEmpty(): Promise<boolean> {
  const userCount = await db.users.count()
  const portfolioCount = await db.portfolios.count()
  const transactionCount = await db.transactions.count()
  const candleCount = await db.candles.count()

  return userCount === 0 && portfolioCount === 0 && transactionCount === 0 && candleCount === 0
}

/**
 * 초기 데이터 로드 (앱 시작 시 자동 실행)
 */
export async function initializeSeedData(): Promise<void> {
  const isEmpty = await isDatabaseEmpty()

  if (isEmpty) {
    logger.info('Database is empty. Loading seed data...')
    const result = await loadAllSeedData()
    logger.info(`Seed data loaded: ${result.candlesLoaded} candles, ${result.transactionsLoaded} transactions`)
  } else {
    logger.info('Database already has data. Skipping seed data load.')
  }
}

/**
 * 목표와 투자 계획 샘플 데이터 생성
 */
async function seedGoalsAndPlans(portfolioId: number) {
  const existingGoals = await goalRepository.findByPortfolioId(portfolioId)
  const existingPlans = await planRepository.findByPortfolioId(portfolioId)

  if (existingGoals.length === 0) {
    await goalRepository.create({
      portfolioId,
      name: 'Retirement 2045',
      targetAmount: 250000,
      targetDate: new Date('2045-12-31'),
      currentAmount: 45000
    })

    await goalRepository.create({
      portfolioId,
      name: 'Children College Fund',
      targetAmount: 120000,
      targetDate: new Date('2036-08-01'),
      currentAmount: 15000
    })
  }

  if (existingPlans.length === 0) {
    await planRepository.create({
      portfolioId,
      symbol: 'AAPL',
      amountPerInterval: 500,
      interval: 'MONTHLY',
      startDate: new Date('2024-01-01'),
      endDate: undefined,
      isActive: true
    })

    await planRepository.create({
      portfolioId,
      symbol: 'SPY',
      amountPerInterval: 300,
      interval: 'WEEKLY',
      startDate: new Date('2024-02-01'),
      endDate: undefined,
      isActive: true
    })
  }
}
