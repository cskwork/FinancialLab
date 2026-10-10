/**
 * Seed Data Loader
 * 데모 데이터를 IndexedDB에 로드하는 유틸리티
 */

import Papa from 'papaparse'
import sampleTransactions from '../../public/data/seeds/sample-transactions.json'
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

/** 데모 로그인 기본 이메일 (LoginPage 기본값 및 schema.populate와 동일) */
export const DEMO_EMAIL = 'demo@finlab.app'

/** 데모 거래 시드를 한 번만 적용했는지 기록하는 localStorage 키 */
export const DEMO_SEED_FLAG = 'finlab_demo_seed_v1'

/** New samples are kept separate from portfolios and records the visitor already owns. */
export const DEMO_PORTFOLIO_NAME = 'FinLab 샘플 포트폴리오 (2024)'

export interface DemoSeedResult {
  demoPortfolioId: number
  candlesLoaded: number
  transactionsLoaded: number
}

function transactionKey(value: TransactionSeed | Transaction): string {
  return JSON.stringify([
    value.type, value.symbol ?? null, value.qty ?? null, value.price ?? null,
    value.fee, new Date(value.timestamp).toISOString(), value.note ?? ''
  ])
}

/** Selects the existing named sample or a complete legacy sample, without changing existing portfolios. */
async function ensureDemoUserAndPortfolio(seeds: TransactionSeed[]): Promise<number> {
  const existingUser = await userRepository.findByEmail(DEMO_EMAIL)
  const userId = existingUser?.id ?? (await userRepository.create(DEMO_EMAIL, 'USER'))
  const portfolios = await portfolioRepository.findByUserId(userId)
  const named = portfolios.find((portfolio) => portfolio.name === DEMO_PORTFOLIO_NAME)
  if (named?.id && (await db.transactions.where('portfolioId').equals(named.id).count()) > 0) return named.id

  const seedKeys = seeds.map(transactionKey)
  for (const portfolio of portfolios) {
    const rows = await db.transactions.where('portfolioId').equals(portfolio.id!).toArray()
    const keys = new Set(rows.map(transactionKey))
    if (seedKeys.length > 0 && seedKeys.every((key) => keys.has(key))) return portfolio.id!
  }

  return named?.id ?? portfolioRepository.create({ userId, name: DEMO_PORTFOLIO_NAME, baseCurrency: 'USD' })
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
  return db.transaction('rw', db.candles, async () => {
    if ((await db.candles.count()) > 0) return 0
    await db.candles.bulkAdd(allCandles)
    return allCandles.length
  })
}

/** Reconciles actual IndexedDB rows. A localStorage marker is never evidence that rows still exist. */
async function ensureDemoPortfolioData(): Promise<{ demoPortfolioId: number; transactionsLoaded: number }> {
  // Bundle the existing fixtures so an already seeded demo can reopen without a network connection.
  const seeds = sampleTransactions.transactions as TransactionSeed[]
  const result = await db.transaction('rw', [db.users, db.portfolios, db.transactions, db.goals, db.plans], async () => {
    const demoPortfolioId = await ensureDemoUserAndPortfolio(seeds)
    const existingCount = await db.transactions.where('portfolioId').equals(demoPortfolioId).count()
    if (existingCount > 0) return { demoPortfolioId, transactionsLoaded: 0 }

    const transactions: Omit<Transaction, 'id'>[] = seeds.map((seed) => ({
      portfolioId: demoPortfolioId,
      type: seed.type,
      symbol: seed.symbol,
      qty: seed.qty,
      price: seed.price,
      fee: seed.fee,
      timestamp: new Date(seed.timestamp),
      note: seed.note
    }))
    await db.transactions.bulkAdd(transactions)
    await seedGoalsAndPlans(demoPortfolioId)
    return { demoPortfolioId, transactionsLoaded: transactions.length }
  })
  writeSeedFlag()
  return result
}

/** Safe initial-load/CTA contract: returns the persisted sample portfolio to select, never clears visitor data. */
export async function ensureDemoPortfolio(): Promise<DemoSeedResult> {
  const candlesLoaded = await ensureCandles()
  return { candlesLoaded, ...(await ensureDemoPortfolioData()) }
}

/** Loads missing samples; repeated calls and concurrent tabs do not duplicate fixtures. */
export async function loadAllSeedData(): Promise<DemoSeedResult> {
  try {
    return await ensureDemoPortfolio()
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
