/**
 * API Client Wrapper
 * Mock ↔ Live 백엔드 토글을 고려한 통합 API 인터페이스
 */

import { z } from 'zod'
import { useConfigStore } from '@/stores/configStore'
import { mockApiClient, type ApiResponse } from './mockClient'
import {
  LoginResponseSchema,
  PortfolioResponseSchema,
  TransactionResponseSchema,
  HoldingResponseSchema,
  GoalResponseSchema,
  PlanResponseSchema,
  MetricSnapshotResponseSchema
} from './schemas'
import type {
  LoginRequest,
  LoginResponse,
  CreatePortfolioRequest,
  PortfolioResponse,
  UpdatePortfolioRequest,
  CreateTransactionRequest,
  TransactionResponse,
  TransactionQuery,
  HoldingResponse,
  GoalResponse,
  CreateGoalRequest,
  PlanResponse,
  CreatePlanRequest,
  MetricSnapshotResponse
} from './schemas'

interface LiveRequestOptions<T> {
  method: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  path: string
  token?: string | null
  body?: unknown
  query?: Record<string, string | number | undefined>
  schema?: z.ZodType<T>
}

async function liveRequest<T>({ method, path, token, body, query, schema }: LiveRequestOptions<T>): Promise<ApiResponse<T>> {
  const configStore = useConfigStore()
  const baseUrl = configStore.backendUrl.replace(/\/$/, '')

  if (!baseUrl) {
    return {
      success: false,
      error: {
        code: 'BACKEND_URL_NOT_CONFIGURED',
        message: '라이브 백엔드 URL이 설정되지 않았습니다.'
      }
    }
  }

  const url = new URL(path, baseUrl)
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        url.searchParams.set(key, String(value))
      }
    })
  }

  try {
    const response = await fetch(url.toString(), {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        'X-Request-Id': crypto.randomUUID()
      },
      body: body ? JSON.stringify(body) : undefined
    })

    if (!response.ok) {
      const errorText = await response.text()
      return {
        success: false,
        error: {
          code: `HTTP_${response.status}`,
          message: errorText || '라이브 백엔드 호출이 실패했습니다.'
        }
      }
    }

    const json = await response.json()

    if (schema) {
      try {
        const parsed = schema.parse(json.data ?? json)
        return {
          success: true,
          data: parsed
        }
      } catch (err) {
        return {
          success: false,
          error: {
            code: 'SCHEMA_VALIDATION_FAILED',
            message: err instanceof Error ? err.message : '응답 스키마 검증 실패'
          }
        }
      }
    }

    return {
      success: true,
      data: json.data ?? json
    }
  } catch (error) {
    return {
      success: false,
      error: {
        code: 'NETWORK_ERROR',
        message: error instanceof Error ? error.message : '네트워크 오류가 발생했습니다.'
      }
    }
  }
}

export interface ApiClientInterface {
  login(request: LoginRequest): Promise<ApiResponse<LoginResponse>>
  getPortfolios(userId: number, token?: string | null): Promise<ApiResponse<PortfolioResponse[]>>
  getPortfolio(id: number, token?: string | null): Promise<ApiResponse<PortfolioResponse>>
  createPortfolio(
    userId: number,
    request: CreatePortfolioRequest,
    token?: string | null
  ): Promise<ApiResponse<PortfolioResponse>>
  updatePortfolio(
    id: number,
    request: UpdatePortfolioRequest,
    token?: string | null
  ): Promise<ApiResponse<PortfolioResponse>>
  deletePortfolio(id: number, token?: string | null): Promise<ApiResponse<void>>
  getTransactions(
    portfolioId: number,
    token?: string | null,
    query?: TransactionQuery
  ): Promise<ApiResponse<TransactionResponse[]>>
  createTransaction(
    request: CreateTransactionRequest,
    token?: string | null
  ): Promise<ApiResponse<TransactionResponse>>
  deleteTransaction(id: number, token?: string | null): Promise<ApiResponse<void>>
  getHoldings(portfolioId: number, token?: string | null): Promise<ApiResponse<HoldingResponse[]>>
  getPortfolioMetrics(portfolioId: number, token?: string | null): Promise<ApiResponse<MetricSnapshotResponse>>
  getGoals(portfolioId: number, token?: string | null): Promise<ApiResponse<GoalResponse[]>>
  createGoal(request: CreateGoalRequest, token?: string | null): Promise<ApiResponse<GoalResponse>>
  deleteGoal(id: number, token?: string | null): Promise<ApiResponse<void>>
  getPlans(portfolioId: number, token?: string | null): Promise<ApiResponse<PlanResponse[]>>
  createPlan(request: CreatePlanRequest, token?: string | null): Promise<ApiResponse<PlanResponse>>
  deletePlan(id: number, token?: string | null): Promise<ApiResponse<void>>
}

export const apiClient: ApiClientInterface = {
  async login(request) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.login(request)
    }

    return liveRequest<LoginResponse>({
      method: 'POST',
      path: '/v1/auth/login',
      body: request,
      schema: LoginResponseSchema
    })
  },

  async getPortfolios(userId, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.getPortfolios(userId)
    }

    return liveRequest<PortfolioResponse[]>({
      method: 'GET',
      path: '/v1/portfolios',
      token,
      query: { userId },
      schema: z.array(PortfolioResponseSchema)
    })
  },

  async getPortfolio(id, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.getPortfolio(id)
    }

    return liveRequest<PortfolioResponse>({
      method: 'GET',
      path: `/v1/portfolios/${id}`,
      token,
      schema: PortfolioResponseSchema
    })
  },

  async createPortfolio(userId, request, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.createPortfolio(userId, request)
    }

    return liveRequest<PortfolioResponse>({
      method: 'POST',
      path: '/v1/portfolios',
      token,
      body: { ...request, userId },
      schema: PortfolioResponseSchema
    })
  },

  async updatePortfolio(id, request, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.updatePortfolio(id, request)
    }

    return liveRequest<PortfolioResponse>({
      method: 'PATCH',
      path: `/v1/portfolios/${id}`,
      token,
      body: request,
      schema: PortfolioResponseSchema
    })
  },

  async deletePortfolio(id, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.deletePortfolio(id)
    }

    return liveRequest<void>({
      method: 'DELETE',
      path: `/v1/portfolios/${id}`,
      token
    })
  },

  async getTransactions(portfolioId, token, query) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.getTransactions(portfolioId)
    }

    return liveRequest<TransactionResponse[]>({
      method: 'GET',
      path: `/v1/portfolios/${portfolioId}/transactions`,
      token,
      query,
      schema: z.array(TransactionResponseSchema)
    })
  },

  async createTransaction(request, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.createTransaction(request)
    }

    return liveRequest<TransactionResponse>({
      method: 'POST',
      path: `/v1/portfolios/${request.portfolioId}/transactions`,
      token,
      body: request,
      schema: TransactionResponseSchema
    })
  },

  async deleteTransaction(id, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.deleteTransaction(id)
    }

    return liveRequest<void>({
      method: 'DELETE',
      path: `/v1/transactions/${id}`,
      token
    })
  },

  async getHoldings(portfolioId, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.getHoldings(portfolioId)
    }

    return liveRequest<HoldingResponse[]>({
      method: 'GET',
      path: `/v1/portfolios/${portfolioId}/holdings`,
      token,
      schema: z.array(HoldingResponseSchema)
    })
  },

  async getPortfolioMetrics(portfolioId, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.getPortfolioMetrics(portfolioId)
    }

    return liveRequest<MetricSnapshotResponse>({
      method: 'GET',
      path: `/v1/metrics/portfolio/${portfolioId}`,
      token,
      schema: MetricSnapshotResponseSchema
    })
  },

  async getGoals(portfolioId, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.getGoals(portfolioId)
    }

    return liveRequest<GoalResponse[]>({
      method: 'GET',
      path: `/v1/portfolios/${portfolioId}/goals`,
      token,
      schema: z.array(GoalResponseSchema)
    })
  },

  async createGoal(request, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.createGoal(request)
    }

    return liveRequest<GoalResponse>({
      method: 'POST',
      path: `/v1/portfolios/${request.portfolioId}/goals`,
      token,
      body: request,
      schema: GoalResponseSchema
    })
  },

  async deleteGoal(id, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.deleteGoal(id)
    }

    return liveRequest<void>({
      method: 'DELETE',
      path: `/v1/goals/${id}`,
      token
    })
  },

  async getPlans(portfolioId, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.getPlans(portfolioId)
    }

    return liveRequest<PlanResponse[]>({
      method: 'GET',
      path: `/v1/portfolios/${portfolioId}/plans`,
      token,
      schema: z.array(PlanResponseSchema)
    })
  },

  async createPlan(request, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.createPlan(request)
    }

    return liveRequest<PlanResponse>({
      method: 'POST',
      path: `/v1/portfolios/${request.portfolioId}/plans`,
      token,
      body: request,
      schema: PlanResponseSchema
    })
  },

  async deletePlan(id, token) {
    const config = useConfigStore()
    if (config.isMockMode) {
      return mockApiClient.deletePlan(id)
    }

    return liveRequest<void>({
      method: 'DELETE',
      path: `/v1/plans/${id}`,
      token
    })
  }
}
