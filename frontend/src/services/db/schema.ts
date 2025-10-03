/**
 * Dexie IndexedDB Schema
 * 데이터베이스 구조 정의 및 버전 관리
 */

import Dexie, { type EntityTable } from 'dexie'

// ========== Type Definitions ==========

export interface User {
  id?: number
  email: string
  role: 'USER' | 'ADMIN'
  createdAt: Date
}

export interface Portfolio {
  id?: number
  userId: number
  name: string
  baseCurrency: string
  createdAt: Date
  updatedAt: Date
}

export interface Transaction {
  id?: number
  portfolioId: number
  type: 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAW'
  symbol?: string // null for DEPOSIT/WITHDRAW
  qty?: number // shares (null for DEPOSIT/WITHDRAW)
  price?: number // per share (null for DEPOSIT/WITHDRAW)
  fee: number // default 0
  timestamp: Date
  note?: string
}

export interface Holding {
  id?: number
  portfolioId: number
  symbol: string
  shares: number
  avgCost: number
  marketValue: number
  unrealizedPnL: number
  lastUpdated: Date
}

export interface Goal {
  id?: number
  portfolioId: number
  name: string
  targetAmount: number
  targetDate: Date
  currentAmount: number
}

export interface Plan {
  id?: number
  portfolioId: number
  symbol: string
  amountPerInterval: number
  interval: 'WEEKLY' | 'MONTHLY'
  startDate: Date
  endDate?: Date
  isActive: boolean
}

export interface Candle {
  id?: number
  symbol: string
  date: string // YYYY-MM-DD format (UTC)
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export interface MetricSnapshot {
  id?: number
  portfolioId: number
  asOf: Date
  totalValue: number
  cash: number
  pnlDaily: number
  pnlYTD: number
  volatility: number
  sharpe: number
  maxDrawdown: number
}

// ========== Database Class ==========

export class FinLabDatabase extends Dexie {
  // 테이블 선언 (타입 추론)
  users!: EntityTable<User, 'id'>
  portfolios!: EntityTable<Portfolio, 'id'>
  transactions!: EntityTable<Transaction, 'id'>
  holdings!: EntityTable<Holding, 'id'>
  goals!: EntityTable<Goal, 'id'>
  plans!: EntityTable<Plan, 'id'>
  candles!: EntityTable<Candle, 'id'>
  metricSnapshots!: EntityTable<MetricSnapshot, 'id'>

  constructor() {
    super('FinLabDB')

    // 버전 1 스키마 정의
    this.version(1).stores({
      users: '++id, email',
      portfolios: '++id, userId, name',
      transactions: '++id, portfolioId, type, symbol, timestamp',
      holdings: '++id, portfolioId, symbol',
      goals: '++id, portfolioId, targetDate',
      plans: '++id, portfolioId, symbol, isActive',
      candles: '++id, symbol, date, [symbol+date]',
      metricSnapshots: '++id, portfolioId, asOf'
    })

    // 마이그레이션 훅 (필요시)
    this.on('populate', () => this.populate())
  }

  /**
   * 초기 데이터 시딩 (선택 사항)
   */
  async populate() {
    // Demo user 생성
    const userId = await this.users.add({
      email: 'demo@finlab.app',
      role: 'USER',
      createdAt: new Date()
    })

    // Demo portfolio 생성
    await this.portfolios.add({
      userId: userId as number,
      name: 'My Portfolio',
      baseCurrency: 'USD',
      createdAt: new Date(),
      updatedAt: new Date()
    })
  }
}

// Singleton 인스턴스 export
export const db = new FinLabDatabase()
