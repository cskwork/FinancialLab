import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { apiClient } from './apiClient'
import { useAuthStore } from '@/stores/authStore'
import { useConfigStore } from '@/stores/configStore'
import { localizeError } from '@/utils/localizedMessages'
import i18n from '@/i18n'
import router from '@/app/router'

vi.mock('@/app/router', async () => {
  const { createMemoryHistory, createRouter } = await import('vue-router')
  return {
    default: createRouter({
      history: createMemoryHistory(),
      routes: ['/', '/login', '/dashboard', '/goals-plans'].map((path) => ({ path, component: { render: () => null } }))
    })
  }
})

const session = { userId: 1, email: 'demo@finlab.app', role: 'USER' as const, token: 'expired-token' }
const unauthorized = { type: 'about:blank', title: 'Unauthorized', status: 401,
  detail: '인증이 필요합니다', instance: '/v1/portfolios/1', code: 'UNAUTHORIZED', message: '인증이 필요합니다' }

function response(body: unknown, status = 400, contentType = 'application/problem+json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })
}

function restoreSession() {
  localStorage.setItem('auth_token', session.token)
  localStorage.setItem('user', JSON.stringify(session))
  useAuthStore().restoreSession()
}

describe('live API errors and expired sessions', () => {
  beforeEach(async () => {
    localStorage.clear()
    setActivePinia(createPinia())
    useConfigStore().setBackendMode('live')
    useConfigStore().setBackendUrl('http://localhost:8080')
    i18n.global.locale.value = 'en'
    await router.replace('/dashboard')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('parses ProblemDetail code, message, and field details instead of showing JSON', async () => {
    const problem = { type: 'about:blank', title: 'Bad Request', status: 400,
      detail: '입력 데이터가 유효하지 않습니다', instance: '/v1/portfolios',
      code: 'VALIDATION_ERROR', message: '입력 데이터가 유효하지 않습니다', details: { fieldErrors: { name: ['must not be blank'] } } }
    vi.stubGlobal('fetch', vi.fn(async () => response(problem, 400, 'application/problem+json; charset=UTF-8')))
    const result = await apiClient.createPortfolio(1, { name: '', baseCurrency: 'USD' }, session.token)
    expect(result).toEqual({ success: false, error: { code: problem.code, message: 'must not be blank', details: problem.details } })
    expect(localizeError(result.error?.message)).toBe('This field is required')
    expect(result.error?.message).not.toContain('"type"')
  })

  it('surfaces and localizes the plan date field error rather than the generic validation message', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response({ code: 'VALIDATION_ERROR', message: '입력 데이터가 유효하지 않습니다',
      details: { fieldErrors: { endDate: ['endDate must be on or after startDate'] } } })))
    const result = await apiClient.createPlan({ portfolioId: 1, symbol: 'AAPL', amountPerInterval: 500,
      interval: 'MONTHLY', isActive: true, startDate: '2026-10-28T00:00:00.000Z', endDate: '2026-10-27T00:00:00.000Z' }, session.token)
    expect(localizeError(result.error?.message)).toBe('End date must be on or after the start date')
    i18n.global.locale.value = 'ko'
    expect(localizeError(result.error?.message)).toBe('종료일은 시작일과 같거나 이후여야 합니다')
  })

  it.each([{}, { fieldErrors: [] }, { fieldErrors: { endDate: [null, {}, '', '  '] } }])(
    'keeps the generic problem message when field details have no readable reasons: %j', async (details) => {
      vi.stubGlobal('fetch', vi.fn(async () => response({ code: 'VALIDATION_ERROR', message: '입력 데이터가 유효하지 않습니다', details })))
      const result = await apiClient.getPortfolio(1, session.token)
      expect(localizeError(result.error?.message)).toBe('The input data is invalid')
    }
  )

  it('accepts a problem without optional details', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response({ code: 'NOT_FOUND', message: '포트폴리오를 찾을 수 없습니다' }, 404)))
    expect(await apiClient.getPortfolio(1, session.token)).toEqual({
      success: false, error: { code: 'NOT_FOUND', message: '포트폴리오를 찾을 수 없습니다' }
    })
  })

  it.each([
    { status: 429, code: 'AUTHENTICATION_RATE_LIMITED', message: '로그인 요청이 너무 많습니다. 잠시 후 다시 시도하세요', english: 'Too many login attempts. Please try again shortly' },
    { status: 503, code: 'AUTHENTICATION_BUSY', message: '로그인 요청을 처리 중입니다. 잠시 후 다시 시도하세요', english: 'Login requests are being processed. Please try again shortly' },
    { status: 409, code: 'CONCURRENT_MODIFICATION', message: '데이터 충돌이 발생했습니다. 다시 시도하세요', english: 'A data conflict occurred. Please try again' },
    { status: 409, code: 'DATA_INTEGRITY', message: '데이터 제약 조건을 위반했습니다', english: 'A data integrity constraint was violated' },
    { status: 404, code: 'NOT_FOUND', message: '리소스를 찾을 수 없습니다', english: 'The resource was not found' }
  ])('localizes the backend $status problem message', async ({ status, code, message, english }) => {
    vi.stubGlobal('fetch', vi.fn(async () => response({ code, message }, status)))
    const result = await apiClient.getPortfolio(1, session.token)
    expect(result.error?.code).toBe(code)
    expect(localizeError(result.error?.message)).toBe(english)
  })

  it.each([
    { name: 'empty message', body: JSON.stringify({ code: 'UNAUTHORIZED', message: '' }), contentType: 'application/problem+json' },
    { name: 'blank message', body: JSON.stringify({ code: 'UNAUTHORIZED', message: '  ' }), contentType: 'application/problem+json' },
    { name: 'nonstring message', body: JSON.stringify({ code: 'UNAUTHORIZED', message: { secret: 'private' } }), contentType: 'application/problem+json' },
    { name: 'blank code', body: JSON.stringify({ code: '  ', message: 'private' }), contentType: 'application/problem+json' },
    { name: 'malformed JSON', body: '{private', contentType: 'application/problem+json' },
    { name: 'unexpected JSON', body: JSON.stringify({ private: 'server details' }), contentType: 'application/json' },
    { name: 'HTML response', body: '<html>private upstream failure</html>', contentType: 'text/html' }
  ])('keeps a safe HTTP fallback for $name', async ({ body, contentType }) => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(body, { status: 401, headers: { 'Content-Type': contentType } })))
    const result = await apiClient.login({ email: session.email, password: 'invalid-password' })
    expect(result).toEqual({ success: false, error: { code: 'HTTP_401', message: '라이브 백엔드 호출이 실패했습니다.' } })
    expect(JSON.stringify(result)).not.toContain('private')
  })

  it('clears Pinia and persisted session and navigates to login on a protected 401', async () => {
    restoreSession()
    vi.stubGlobal('fetch', vi.fn(async () => response(unauthorized, 401)))
    expect(useAuthStore().isAuthenticated).toBe(true)
    const result = await apiClient.getPortfolio(1, session.token)
    expect(result.error).toEqual({ code: 'UNAUTHORIZED', message: unauthorized.message })
    expect(useAuthStore().user).toBeNull()
    expect(useAuthStore().token).toBeNull()
    expect(useAuthStore().isAuthenticated).toBe(false)
    expect(localStorage.getItem('user')).toBeNull()
    expect(localStorage.getItem('auth_token')).toBeNull()
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/dashboard')
  })

  it('preserves the page, query and fragment in the expired-session login redirect', async () => {
    restoreSession()
    await router.replace('/goals-plans?tab=plans#new-plan')
    vi.stubGlobal('fetch', vi.fn(async () => response(unauthorized, 401)))
    await apiClient.getGoals(1, session.token)
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/goals-plans?tab=plans#new-plan')
  })

  it('expires the session even when a protected 401 has an invalid error body', async () => {
    restoreSession()
    vi.stubGlobal('fetch', vi.fn(async () => response({ code: 'UNAUTHORIZED', message: null }, 401)))
    const result = await apiClient.getPortfolio(1, session.token)
    expect(result.error?.code).toBe('HTTP_401')
    expect(useAuthStore().isAuthenticated).toBe(false)
    expect(localStorage.getItem('auth_token')).toBeNull()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('keeps failed login on the form without expiring an existing session', async () => {
    restoreSession()
    await router.replace('/login')
    const logout = vi.spyOn(useAuthStore(), 'logout')
    const replace = vi.spyOn(router, 'replace')
    vi.stubGlobal('fetch', vi.fn(async () => response({ ...unauthorized,
      code: 'INVALID_CREDENTIALS', message: '이메일 또는 비밀번호가 올바르지 않습니다' }, 401)))
    expect(await useAuthStore().login(session.email, 'wrong-password')).toBe(false)
    expect(localizeError(useAuthStore().error)).toBe('Email or password is incorrect')
    expect(logout).not.toHaveBeenCalled()
    expect(replace).not.toHaveBeenCalled()
    expect(useAuthStore().token).toBe(session.token)
    expect(localStorage.getItem('auth_token')).toBe(session.token)
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('does not expire the session for a forbidden response', async () => {
    restoreSession()
    vi.stubGlobal('fetch', vi.fn(async () => response({ code: 'FORBIDDEN', message: 'Access denied' }, 403)))
    expect((await apiClient.getPortfolio(1, session.token)).error?.code).toBe('FORBIDDEN')
    expect(useAuthStore().isAuthenticated).toBe(true)
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('coalesces simultaneous protected 401s and does not redirect again from login', async () => {
    restoreSession()
    const replace = vi.spyOn(router, 'replace')
    vi.stubGlobal('fetch', vi.fn(async () => response(unauthorized, 401)))
    await Promise.all([apiClient.getPortfolio(1, session.token), apiClient.getHoldings(1, session.token)])
    expect(replace).toHaveBeenCalledTimes(1)
    expect(replace).toHaveBeenCalledWith({ path: '/login', query: { redirect: '/dashboard' } })
    await apiClient.getPortfolio(1, session.token)
    expect(replace).toHaveBeenCalledTimes(1)
  })

  it('accepts the legacy passwordless demo request only in mock mode', async () => {
    useConfigStore().setBackendMode('mock')
    vi.stubGlobal('fetch', vi.fn())
    expect(await apiClient.login({ email: session.email })).toMatchObject({ success: true, data: { email: session.email } })
    expect(fetch).not.toHaveBeenCalled()
  })

  it('rejects a passwordless non-demo request in mock mode', async () => {
    useConfigStore().setBackendMode('mock')
    vi.stubGlobal('fetch', vi.fn())
    expect(await apiClient.login({ email: 'other@example.com' })).toMatchObject({ success: false, error: { code: 'VALIDATION_ERROR' } })
    expect(fetch).not.toHaveBeenCalled()
  })

  it('validates an explicitly supplied demo password in mock mode', async () => {
    useConfigStore().setBackendMode('mock')
    vi.stubGlobal('fetch', vi.fn())
    expect(await apiClient.login({ email: session.email, password: '' })).toMatchObject({ success: false, error: { code: 'VALIDATION_ERROR' } })
    expect(fetch).not.toHaveBeenCalled()
  })

  it('sends a UUID Idempotency-Key and reuses it for an unchanged failed transaction request', async () => {
    const request = { portfolioId: 1, type: 'BUY' as const, symbol: 'AAPL', qty: 1, price: 100, fee: 1,
      timestamp: '2026-10-28T00:00:00.000Z' }
    const fetch = vi.fn(async (_url: string, _init?: RequestInit) => { throw new TypeError('Network request interrupted') })
    vi.stubGlobal('fetch', fetch)
    await apiClient.createTransaction(request, session.token)
    await apiClient.createTransaction(request, session.token)
    const headers = fetch.mock.calls.map((call) => (call[1] as RequestInit).headers as Record<string, string>)
    expect(headers[0]?.['Idempotency-Key']).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i)
    expect(headers[1]?.['Idempotency-Key']).toBe(headers[0]?.['Idempotency-Key'])
    request.qty = 2
    await apiClient.createTransaction(request, session.token)
    expect(((fetch.mock.calls[2]?.[1] as RequestInit).headers as Record<string, string>)['Idempotency-Key'])
      .not.toBe(headers[0]?.['Idempotency-Key'])
  })

  it('uses the explicit submit key for retries with reconstructed transaction bodies', async () => {
    const request = { portfolioId: 1, type: 'BUY' as const, symbol: 'AAPL', qty: 1, price: 100, fee: 1,
      timestamp: '2026-10-28T00:00:00.000Z' }
    const fetch = vi.fn(async (_url: string, _init?: RequestInit) => response({ code: 'AUTHENTICATION_BUSY', message: 'Please retry' }, 503))
    vi.stubGlobal('fetch', fetch)
    const key = crypto.randomUUID()
    await apiClient.createTransaction({ ...request }, session.token, key)
    await apiClient.createTransaction({ ...request }, session.token, key)
    expect(fetch.mock.calls.map((call) => (call[1]?.headers as Record<string, string>)['Idempotency-Key']))
      .toEqual([key, key])
  })

  it('uses a new key when the same transaction object is deliberately submitted after success', async () => {
    const request = { portfolioId: 1, type: 'BUY' as const, symbol: 'AAPL', qty: 1, price: 100, fee: 1,
      timestamp: '2026-10-28T00:00:00.000Z' }
    const fetch = vi.fn(async (_url: string, _init?: RequestInit) => response({ ...request, id: 1 }, 201, 'application/json'))
    vi.stubGlobal('fetch', fetch)
    expect((await apiClient.createTransaction(request, session.token)).success).toBe(true)
    await apiClient.createTransaction(request, session.token)
    expect((fetch.mock.calls[0]?.[1]?.headers as Record<string, string>)['Idempotency-Key'])
      .not.toBe((fetch.mock.calls[1]?.[1]?.headers as Record<string, string>)['Idempotency-Key'])
  })

  it('keeps mock transaction creation local without a fetch or idempotency UUID', async () => {
    useConfigStore().setBackendMode('mock')
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)
    const uuid = vi.spyOn(crypto, 'randomUUID')
    await apiClient.createTransaction({ portfolioId: -1, type: 'BUY', symbol: 'AAPL', qty: 1, price: 100, fee: 1,
      timestamp: '2026-10-28T00:00:00.000Z' })
    expect(fetch).not.toHaveBeenCalled()
    expect(uuid).not.toHaveBeenCalled()
  })
})
