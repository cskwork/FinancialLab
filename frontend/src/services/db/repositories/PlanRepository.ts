/**
 * Plan Repository
 * 적립식 투자 계획 데이터 접근 계층
 */

import { db, type Plan } from '../schema'

export class PlanRepository {
  async findByPortfolioId(portfolioId: number): Promise<Plan[]> {
    return await db.plans.where('portfolioId').equals(portfolioId).toArray()
  }

  async create(plan: Omit<Plan, 'id'>): Promise<number> {
    const id = await db.plans.add(plan)
    return id as number
  }

  async update(id: number, updates: Partial<Omit<Plan, 'id'>>): Promise<number> {
    return await db.plans.update(id, updates)
  }

  async delete(id: number): Promise<void> {
    await db.plans.delete(id)
  }
}

export const planRepository = new PlanRepository()
