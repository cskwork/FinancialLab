/**
 * Transaction Repository
 * 거래 데이터 접근 계층
 */

import { db, type Transaction } from '../schema'

export class TransactionRepository {
  /**
   * 포트폴리오별 거래 조회 (최신순)
   */
  async findByPortfolioId(portfolioId: number): Promise<Transaction[]> {
    return await db.transactions
      .where('portfolioId')
      .equals(portfolioId)
      .reverse()
      .sortBy('timestamp')
  }

  /**
   * 거래 생성
   */
  async create(data: Omit<Transaction, 'id'>): Promise<number> {
    const id = await db.transactions.add({
      ...data,
      fee: data.fee ?? 0
    })
    return id as number
  }

  /**
   * 대량 거래 생성 (CSV import용)
   */
  async bulkCreate(transactions: Omit<Transaction, 'id'>[]): Promise<number> {
    const ids = await db.transactions.bulkAdd(
      transactions.map(t => ({ ...t, fee: t.fee ?? 0 })),
      { allKeys: true }
    )
    return ids.length
  }

  /**
   * ID로 거래 조회
   */
  async findById(id: number): Promise<Transaction | undefined> {
    return await db.transactions.get(id)
  }

  /**
   * 거래 업데이트
   */
  async update(id: number, data: Partial<Omit<Transaction, 'id'>>): Promise<number> {
    return await db.transactions.update(id, data)
  }

  /**
   * 거래 삭제
   */
  async delete(id: number): Promise<void> {
    await db.transactions.delete(id)
  }

  /**
   * 심볼별 거래 조회
   */
  async findBySymbol(portfolioId: number, symbol: string): Promise<Transaction[]> {
    return await db.transactions
      .where('[portfolioId+symbol]')
      .equals([portfolioId, symbol])
      .sortBy('timestamp')
  }

  /**
   * 기간별 거래 조회
   */
  async findByDateRange(
    portfolioId: number,
    startDate: Date,
    endDate: Date
  ): Promise<Transaction[]> {
    return await db.transactions
      .where('portfolioId')
      .equals(portfolioId)
      .and(tx => tx.timestamp >= startDate && tx.timestamp <= endDate)
      .sortBy('timestamp')
  }
}

// Singleton export
export const transactionRepository = new TransactionRepository()
