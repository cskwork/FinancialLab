/**
 * API Request/Response Schemas (Zod)
 * 타입 안전성과 런타임 검증을 위한 스키마 정의
 */

import { z } from 'zod'

// ========== Common Schemas ==========

export const ApiErrorSchema = z.object({
  code: z.string(),
  message: z.string(),
  details: z.record(z.string(), z.unknown()).optional()
})

export type ApiError = z.infer<typeof ApiErrorSchema>

// ========== Auth Schemas ==========

export const LoginRequestSchema = z.object({
  email: z.string().email('유효한 이메일 주소를 입력하세요')
})

export const LoginResponseSchema = z.object({
  userId: z.number(),
  email: z.string().email(),
  role: z.enum(['USER', 'ADMIN']),
  token: z.string()
})

export type LoginRequest = z.infer<typeof LoginRequestSchema>
export type LoginResponse = z.infer<typeof LoginResponseSchema>

// ========== Portfolio Schemas ==========

export const CreatePortfolioRequestSchema = z.object({
  name: z.string().min(1, '포트폴리오 이름은 필수입니다').max(100),
  baseCurrency: z.string().length(3, '통화 코드는 3자리여야 합니다').default('USD')
})

export const UpdatePortfolioRequestSchema = z.object({
  name: z.string().min(1).max(100).optional(),
  baseCurrency: z.string().length(3).optional()
})

export const PortfolioResponseSchema = z.object({
  id: z.number(),
  userId: z.number(),
  name: z.string(),
  baseCurrency: z.string(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime()
})

export type CreatePortfolioRequest = z.infer<typeof CreatePortfolioRequestSchema>
export type UpdatePortfolioRequest = z.infer<typeof UpdatePortfolioRequestSchema>
export type PortfolioResponse = z.infer<typeof PortfolioResponseSchema>

// ========== Transaction Schemas ==========

export const TransactionTypeSchema = z.enum(['BUY', 'SELL', 'DEPOSIT', 'WITHDRAW'])

export const CreateTransactionRequestSchema = z.object({
  portfolioId: z.number(),
  type: TransactionTypeSchema,
  symbol: z.string().optional(),
  qty: z.number().positive().optional(),
  price: z.number().positive().optional(),
  fee: z.number().nonnegative().default(0),
  timestamp: z.string().datetime(),
  note: z.string().max(500).optional()
}).refine(
  (data) => {
    // BUY/SELL은 symbol, qty, price 필수
    if (data.type === 'BUY' || data.type === 'SELL') {
      return data.symbol && data.qty && data.price
    }
    return true
  },
  {
    message: 'BUY/SELL 거래는 symbol, qty, price가 필수입니다'
  }
)

export const TransactionResponseSchema = z.object({
  id: z.number(),
  portfolioId: z.number(),
  type: TransactionTypeSchema,
  symbol: z.string().optional(),
  qty: z.number().optional(),
  price: z.number().optional(),
  fee: z.number(),
  timestamp: z.string().datetime(),
  note: z.string().optional()
})

export const TransactionQuerySchema = z.object({
  portfolioId: z.number(),
  startDate: z.string().datetime().optional(),
  endDate: z.string().datetime().optional(),
  type: TransactionTypeSchema.optional(),
  symbol: z.string().optional()
})

export type CreateTransactionRequest = z.infer<typeof CreateTransactionRequestSchema>
export type TransactionResponse = z.infer<typeof TransactionResponseSchema>
export type TransactionQuery = z.infer<typeof TransactionQuerySchema>

// ========== Holding Schemas ==========

export const HoldingResponseSchema = z.object({
  id: z.number(),
  portfolioId: z.number(),
  symbol: z.string(),
  shares: z.number(),
  avgCost: z.number(),
  marketValue: z.number(),
  unrealizedPnL: z.number(),
  lastUpdated: z.string().datetime()
})

export type HoldingResponse = z.infer<typeof HoldingResponseSchema>

// ========== Market Data Schemas ==========

export const CandleResponseSchema = z.object({
  symbol: z.string(),
  date: z.string(), // YYYY-MM-DD
  open: z.number(),
  high: z.number(),
  low: z.number(),
  close: z.number(),
  volume: z.number()
})

export const MarketDataQuerySchema = z.object({
  symbol: z.string(),
  startDate: z.string().optional(),
  endDate: z.string().optional()
})

export type CandleResponse = z.infer<typeof CandleResponseSchema>
export type MarketDataQuery = z.infer<typeof MarketDataQuerySchema>

// ========== Goal Schemas ==========

export const CreateGoalRequestSchema = z.object({
  portfolioId: z.number(),
  name: z.string().min(1, '목표 이름은 필수입니다').max(100),
  targetAmount: z.number().positive('목표 금액은 양수여야 합니다'),
  targetDate: z.string().datetime('유효한 날짜를 입력하세요'),
  currentAmount: z.number().nonnegative().default(0)
})

export const GoalResponseSchema = z.object({
  id: z.number(),
  portfolioId: z.number(),
  name: z.string(),
  targetAmount: z.number(),
  targetDate: z.string().datetime(),
  currentAmount: z.number()
})

export type CreateGoalRequest = z.infer<typeof CreateGoalRequestSchema>
export type GoalResponse = z.infer<typeof GoalResponseSchema>

// ========== Plan Schemas ==========

export const PlanIntervalSchema = z.enum(['WEEKLY', 'MONTHLY'])

export const CreatePlanRequestSchema = z.object({
  portfolioId: z.number(),
  symbol: z.string().min(1, '심볼은 필수입니다'),
  amountPerInterval: z.number().positive('투자 금액은 양수여야 합니다'),
  interval: PlanIntervalSchema,
  startDate: z.string().datetime(),
  endDate: z.string().datetime().optional(),
  isActive: z.boolean().default(true)
})

export const PlanResponseSchema = z.object({
  id: z.number(),
  portfolioId: z.number(),
  symbol: z.string(),
  amountPerInterval: z.number(),
  interval: PlanIntervalSchema,
  startDate: z.string().datetime(),
  endDate: z.string().datetime().optional(),
  isActive: z.boolean()
})

export type CreatePlanRequest = z.infer<typeof CreatePlanRequestSchema>
export type PlanResponse = z.infer<typeof PlanResponseSchema>

// ========== Metric Schemas ==========

export const MetricSnapshotResponseSchema = z.object({
  id: z.number(),
  portfolioId: z.number(),
  asOf: z.string().datetime(),
  totalValue: z.number(),
  cash: z.number(),
  pnlDaily: z.number(),
  pnlYTD: z.number(),
  volatility: z.number(),
  sharpe: z.number(),
  maxDrawdown: z.number()
})

export type MetricSnapshotResponse = z.infer<typeof MetricSnapshotResponseSchema>
