/**
 * Portfolio Repository
 * 포트폴리오 데이터 접근 계층
 */

import { db, type Portfolio } from '../schema'

export class PortfolioRepository {
  /**
   * 사용자별 포트폴리오 조회
   */
  async findByUserId(userId: number): Promise<Portfolio[]> {
    return await db.portfolios
      .where('userId')
      .equals(userId)
      .toArray()
  }

  /**
   * ID로 포트폴리오 조회
   */
  async findById(id: number): Promise<Portfolio | undefined> {
    return await db.portfolios.get(id)
  }

  /**
   * 포트폴리오 생성
   */
  async create(data: Omit<Portfolio, 'id' | 'createdAt' | 'updatedAt'>): Promise<number> {
    const now = new Date()
    const id = await db.portfolios.add({
      ...data,
      createdAt: now,
      updatedAt: now
    })
    return id as number
  }

  /**
   * 포트폴리오 업데이트
   */
  async update(id: number, data: Partial<Omit<Portfolio, 'id' | 'createdAt'>>): Promise<number> {
    return await db.portfolios.update(id, {
      ...data,
      updatedAt: new Date()
    })
  }

  /**
   * 포트폴리오 삭제
   */
  async delete(id: number): Promise<void> {
    // 관련 데이터도 삭제 (cascade)
    await db.transaction('rw', [db.portfolios, db.transactions, db.holdings, db.goals, db.plans], async () => {
      await db.portfolios.delete(id)
      await db.transactions.where('portfolioId').equals(id).delete()
      await db.holdings.where('portfolioId').equals(id).delete()
      await db.goals.where('portfolioId').equals(id).delete()
      await db.plans.where('portfolioId').equals(id).delete()
    })
  }

  /**
   * 모든 포트폴리오 조회
   */
  async findAll(): Promise<Portfolio[]> {
    return await db.portfolios.toArray()
  }
}

// Singleton export
export const portfolioRepository = new PortfolioRepository()
