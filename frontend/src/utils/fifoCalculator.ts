/**
 * FIFO (First-In-First-Out) Holdings Calculator
 * 거래 내역을 기반으로 현재 보유 주식을 FIFO 방식으로 계산
 */

import Decimal from 'decimal.js'
import type { Transaction } from '@/services/db/schema'

/**
 * FIFO 로트 (매수 단위)
 */
interface FifoLot {
  qty: Decimal
  price: Decimal
  purchaseDate: Date
}

/**
 * 보유 포지션
 */
export interface Holding {
  symbol: string
  totalShares: number
  avgCost: number
  lots: FifoLot[]
}

/**
 * 거래 처리 결과 (매도 시)
 */
export interface SaleResult {
  symbol: string
  qtyBuy: number
  proceeds: number
  costBasis: number
  realizedPnL: number
}

/**
 * FIFO 계산기
 */
export class FifoCalculator {
  // symbol -> Holding 매핑
  private holdings: Map<string, Holding>
  private sales: SaleResult[] = []

  constructor() {
    this.holdings = new Map()
  }

  /**
   * 거래 목록을 처리하여 현재 holdings 계산
   */
  processBuy(transactions: Transaction[]): Map<string, Holding> {
    // 시간순 정렬
    const sorted = [...transactions].sort(
      (a, b) => a.timestamp.getTime() - b.timestamp.getTime()
    )

    this.holdings.clear()
    this.sales = []

    for (const tx of sorted) {
      if (tx.type === 'BUY') {
        this.addBuyTransaction(tx)
      } else if (tx.type === 'SELL') {
        this.sales.push(this.addSellTransaction(tx))
      }
      // DEPOSIT/WITHDRAW는 holdings에 영향 없음
    }

    return this.holdings
  }

  /**
   * 매수 거래 추가
   */
  private addBuyTransaction(tx: Transaction): void {
    if (!tx.symbol || tx.qty === undefined || tx.price === undefined) {
      throw new Error('BUY 거래는 symbol, qty, price가 필수입니다')
    }

    const symbol = tx.symbol
    const qty = new Decimal(tx.qty)
    // Allocate the buy fee per share so partial FIFO sales retain their share of it.
    const price = new Decimal(tx.price).plus(new Decimal(tx.fee).dividedBy(qty))

    // 기존 holding 조회 또는 생성
    let holding = this.holdings.get(symbol)
    if (!holding) {
      holding = {
        symbol,
        totalShares: 0,
        avgCost: 0,
        lots: []
      }
      this.holdings.set(symbol, holding)
    }

    // 새 로트 추가
    holding.lots.push({
      qty,
      price,
      purchaseDate: tx.timestamp
    })

    // totalShares 및 avgCost 재계산
    this.recalculateHolding(holding)
  }

  /**
   * 매도 거래 추가
   */
  private addSellTransaction(tx: Transaction): SaleResult {
    if (!tx.symbol || tx.qty === undefined || tx.price === undefined) {
      throw new Error('SELL 거래는 symbol, qty, price가 필수입니다')
    }

    const symbol = tx.symbol
    const sellQty = new Decimal(tx.qty)
    const sellPrice = new Decimal(tx.price)

    const holding = this.holdings.get(symbol)
    if (!holding) {
      throw new Error(`${symbol}에 대한 보유 주식이 없습니다`)
    }

    // FIFO: 가장 오래된 로트부터 매도
    let remainingQty = sellQty
    let totalCostBasis = new Decimal(0)

    const lotsToRemove: number[] = []

    for (let i = 0; i < holding.lots.length; i++) {
      if (remainingQty.lte(0)) break

      const lot = holding.lots[i]
      if (!lot) continue // TypeScript guard

      if (lot.qty.lte(remainingQty)) {
        // 로트 전체 매도
        totalCostBasis = totalCostBasis.plus(lot.qty.times(lot.price))
        remainingQty = remainingQty.minus(lot.qty)
        lotsToRemove.push(i)
      } else {
        // 로트 일부 매도
        totalCostBasis = totalCostBasis.plus(remainingQty.times(lot.price))
        lot.qty = lot.qty.minus(remainingQty)
        remainingQty = new Decimal(0)
      }
    }

    // 완전히 매도된 로트 제거 (역순으로)
    for (let i = lotsToRemove.length - 1; i >= 0; i--) {
      const indexToRemove = lotsToRemove[i]
      if (indexToRemove !== undefined) {
        holding.lots.splice(indexToRemove, 1)
      }
    }

    if (remainingQty.gt(0)) {
      throw new Error(`${symbol} 매도 수량이 보유 수량을 초과합니다`)
    }

    // holding 재계산
    this.recalculateHolding(holding)

    // holding이 비었으면 제거
    if (holding.totalShares === 0) {
      this.holdings.delete(symbol)
    }

    // 실현 손익 계산
    const proceeds = sellQty.times(sellPrice).minus(tx.fee)
    const realizedPnL = proceeds.minus(totalCostBasis)

    return {
      symbol,
      qtyBuy: sellQty.toNumber(),
      proceeds: proceeds.toNumber(),
      costBasis: totalCostBasis.toNumber(),
      realizedPnL: realizedPnL.toNumber()
    }
  }

  /**
   * Holding의 totalShares 및 avgCost 재계산
   */
  private recalculateHolding(holding: Holding): void {
    if (holding.lots.length === 0) {
      holding.totalShares = 0
      holding.avgCost = 0
      return
    }

    let totalShares = new Decimal(0)
    let totalCost = new Decimal(0)

    for (const lot of holding.lots) {
      totalShares = totalShares.plus(lot.qty)
      totalCost = totalCost.plus(lot.qty.times(lot.price))
    }

    holding.totalShares = totalShares.toNumber()
    holding.avgCost = totalCost.dividedBy(totalShares).toNumber()
  }

  /** Realized FIFO sales from the most recent ledger replay. */
  getSales(): SaleResult[] {
    return this.sales.map((sale) => ({ ...sale }))
  }

  /** 현재 holdings 조회 */
  getHoldings(): Map<string, Holding> {
    return this.holdings
  }

  /**
   * 특정 심볼의 holding 조회
   */
  getHolding(symbol: string): Holding | undefined {
    return this.holdings.get(symbol)
  }

  /**
   * 미실현 손익 계산
   */
  calculateUnrealizedPnL(symbol: string, currentPrice: number): number {
    const holding = this.holdings.get(symbol)
    if (!holding) {
      return 0
    }

    const marketValue = new Decimal(holding.totalShares).times(currentPrice)
    const costBasis = new Decimal(holding.totalShares).times(holding.avgCost)

    return marketValue.minus(costBasis).toNumber()
  }
}

/**
 * 거래 목록으로부터 holdings 계산 (헬퍼 함수)
 */
export function calculateHoldingsFromTransactions(
  transactions: Transaction[]
): Map<string, Holding> {
  const calculator = new FifoCalculator()
  return calculator.processBuy(transactions)
}
