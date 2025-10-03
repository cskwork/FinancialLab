/**
 * Goal Repository
 * 재무 목표 데이터 접근 계층
 */

import { db, type Goal } from '../schema'

export class GoalRepository {
  async findByPortfolioId(portfolioId: number): Promise<Goal[]> {
    return await db.goals.where('portfolioId').equals(portfolioId).toArray()
  }

  async create(goal: Omit<Goal, 'id'>): Promise<number> {
    const id = await db.goals.add(goal)
    return id as number
  }

  async update(id: number, updates: Partial<Omit<Goal, 'id'>>): Promise<number> {
    return await db.goals.update(id, updates)
  }

  async delete(id: number): Promise<void> {
    await db.goals.delete(id)
  }
}

export const goalRepository = new GoalRepository()
