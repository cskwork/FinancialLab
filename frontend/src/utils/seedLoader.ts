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

/** 데모 로그인 기본 이메일 (LoginPage 기본값 및 schema.populate와 동일) */
export const DEMO_EMAIL = 'demo@finlab.app'

/** 데모 거래 시드를 한 번만 적용했는지 기록하는 localStorage 키 */
export const DEMO_SEED_FLAG = 'finlab_demo_seed_v1'

/**
 * 데모 사용자 및 포트폴리오 조회/생성
 * schema.populate()가 만든 demo@finlab.app 사용자와 첫 포트폴리오를 재사용한다.
 */
async function ensureDemoUserAndPortfolio(): Promise<{ userId: number; portfolioId: number }> {
  const existingUser = await userRepository.findByEmail(DEMO_EMAIL)
  const userId = existingUser?.id ?? (await userRepository.create(DEMO_EMAIL, 'USER'))

  const existingPortfolios = await portfolioRepository.findByUserId(userId)
  const firstPortfolio = existingPortfolios[0]

  const portfolioId =
    firstPortfolio?.id ??
    (await portfolioRepository.create({
      userId,
      name: 'Demo Portfolio',
      baseCurrency: 'USD'
    }))

  return { userId, portfolioId }
}

function readSeedFlag(): boolean {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(DEMO_SEED_FLAG) === 'done'
  } catch {
    return false
  }
}

function writeSeedFlag() {
  try {
    localStorage.setItem(DEMO_SEED_FLAG, 'done')
  } catch {
    // storage unavailable (private mode): seeding simply re-checks next visit
  }
}

/**
 * OHLCV 캔들이 비어 있으면 로드 (백테스트 랩 심볼 목록의 원천)
 */
async function ensureCandles(): Promise<number> {
  if ((await db.candles.count()) > 0) return 0

  const [aaplCandles, spyCandles] = await Promise.all([
    loadOHLCVData('AAPL', '/data/ohlcv/AAPL.csv'),
    loadOHLCVData('SPY', '/data/ohlcv/SPY.csv')
  ])
  const allCandles = [...aaplCandles, ...spyCandles]
  await db.candles.bulkAdd(allCandles)
  return allCandles.length
}

/**
 * 데모 포트폴리오에 샘플 거래/목표/계획을 채운다.
 * 사용자가 직접 입력한 데이터는 절대 지우지 않는다: 포트폴리오에 거래가 하나라도 있으면 건너뛴다.
 */
async function ensureDemoPortfolioData(): Promise<number> {
  if (readSeedFlag()) return 0

  const { portfolioId } = await ensureDemoUserAndPortfolio()
  const existingCount = await db.transactions.where('portfolioId').equals(portfolioId).count()
  if (existingCount > 0) {
    writeSeedFlag()
    return 0
  }

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

  await db.transactions.bulkAdd(transactions)
  await seedGoalsAndPlans(portfolioId)
  writeSeedFlag()
  return transactions.length
}

/**
 * 전체 seed 데이터 로드 (비파괴적, 여러 번 호출해도 안전)
 */
export async function loadAllSeedData(): Promise<{
  candlesLoaded: number
  transactionsLoaded: number
}> {
  try {
    const candlesLoaded = await ensureCandles()
    const transactionsLoaded = await ensureDemoPortfolioData()
    return { candlesLoaded, transactionsLoaded }
  } catch (error) {
    logger.error('Failed to load seed data', error)
    throw error
  }
}

let seedPromise: Promise<void> | null = null

/**
 * 초기 데이터 로드 (앱 시작 시 자동 실행)
 * 이전 버전은 schema.populate()가 만든 데모 사용자 때문에 DB를 "비어있지 않음"으로 판단해
 * 캔들과 샘플 거래를 한 번도 로드하지 못했다. 이제 각 데이터셋을 개별적으로 확인한다.
 */
export function initializeSeedData(): Promise<void> {
  if (!seedPromise) {
    seedPromise = loadAllSeedData().then((result) => {
      logger.info(
        `Seed check complete: ${result.candlesLoaded} candles, ${result.transactionsLoaded} transactions added`
      )
    })
  }
  return seedPromise
}

/**
 * 시드 로딩 완료까지 대기 (실패해도 앱 진입은 막지 않는다)
 */
export async function whenSeedReady(): Promise<void> {
  if (!seedPromise) return
  try {
    await seedPromise
  } catch {
    // 오류는 initializeSeedData 호출부에서 이미 로깅됨
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
