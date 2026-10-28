import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { z } from 'zod'
import * as schemas from './schemas'

const directory = resolve(process.cwd(), '../contract')
const problem = schemas.ApiErrorSchema.and(z.object({
  type: z.string().startsWith('urn:finlab:problem:'), title: z.string(), status: z.number().int(),
  detail: z.string(), instance: z.string()
})).refine(value => value.type === `urn:finlab:problem:${value.code.toLowerCase().replaceAll('_', '-')}`, {
  message: 'Problem type must match its FinLab problem code'
})
const fixtures: Record<string, z.ZodType> = {
  login: schemas.LoginResponseSchema,
  portfolio: schemas.PortfolioResponseSchema,
  portfolios: z.array(schemas.PortfolioResponseSchema),
  transaction: schemas.TransactionResponseSchema,
  transactions: z.array(schemas.TransactionResponseSchema),
  holdings: z.array(schemas.HoldingResponseSchema),
  goal: schemas.GoalResponseSchema,
  goals: z.array(schemas.GoalResponseSchema),
  plan: schemas.PlanResponseSchema,
  plans: z.array(schemas.PlanResponseSchema),
  metrics: schemas.MetricSnapshotResponseSchema,
  'market-data': z.array(schemas.CandleResponseSchema),
  delete: z.object({ success: z.literal(true) }),
  'error-validation': problem,
  'error-unauthorized': problem,
  'error-forbidden': problem,
  'error-not-found': problem,
  'error-idempotency': problem
}

describe('shared backend HTTP response contract', () => {
  it('assigns a schema to every shared JSON fixture', () => {
    expect(readdirSync(directory).filter(name => name.endsWith('.json')).sort())
      .toEqual(Object.keys(fixtures).map(name => `${name}.json`).sort())
  })

  for (const [name, schema] of Object.entries(fixtures)) {
    it(`${name}.json parses with the frontend contract`, () => {
      const value: unknown = JSON.parse(readFileSync(`${directory}/${name}.json`, 'utf8'))
      expect(() => schema.parse(value)).not.toThrow()
    })
  }
})

// CI sets this after the backend integration tests have captured real HTTP bodies.
const actualDirectory = process.env.FINLAB_CONTRACT_ACTUAL_DIR
if (actualDirectory) {
  describe('actual backend HTTP responses', () => {
    it('captures every committed response shape', () => {
      expect(readdirSync(actualDirectory).filter(name => name.endsWith('.json')).sort())
        .toEqual(Object.keys(fixtures).map(name => `${name}.json`).sort())
    })
    for (const [name, schema] of Object.entries(fixtures)) {
      it(`${name}.json from the real backend parses with Zod`, () => {
        const actual: unknown = JSON.parse(readFileSync(`${actualDirectory}/${name}.json`, 'utf8'))
        expect(() => schema.parse(actual)).not.toThrow()
      })
    }
  })
}
