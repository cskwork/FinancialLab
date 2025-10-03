/**
 * Metric Snapshot Repository
 * 포트폴리오 지표 스냅샷 CRUD
 */

import { db, type MetricSnapshot } from '../schema'

export class MetricSnapshotRepository {
  async findLatestByPortfolioId(portfolioId: number): Promise<MetricSnapshot | undefined> {
    return await db.metricSnapshots
      .where('portfolioId')
      .equals(portfolioId)
      .sortBy('asOf')
      .then((results) => results.at(-1))
  }

  async create(snapshot: Omit<MetricSnapshot, 'id'>): Promise<number> {
    const id = await db.metricSnapshots.add(snapshot)
    return id as number
  }

  async clearForPortfolio(portfolioId: number): Promise<void> {
    await db.metricSnapshots.where('portfolioId').equals(portfolioId).delete()
  }
}

export const metricSnapshotRepository = new MetricSnapshotRepository()
