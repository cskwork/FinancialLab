import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App, type Component } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import i18n from '@/i18n'
import Decimal from 'decimal.js'
import { apiClient } from '@/services/api'
import { useAuthStore } from '@/stores/authStore'
import { useConfigStore } from '@/stores/configStore'
import { usePortfolioStore } from '@/stores/portfolioStore'
import { useGoalPlanStore } from '@/stores/goalPlanStore'
import { useTransactionStore } from '@/stores/transactionStore'
import { useAnalyticsStore } from '@/stores/analyticsStore'
import GoalsPlansPage from './GoalsPlansPage.vue'
import TransactionsPage from './TransactionsPage.vue'

let app: App | undefined
let router: Router

async function settle() {
  for (let i = 0; i < 4; i++) {
    await new Promise(resolve => setTimeout(resolve, 0))
    await nextTick()
  }
}

async function mount(page: Component, path: string) {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }]
  })
  await router.push(path)
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(page)
  // The active Pinia is shared with the stores prepared by each test.
  app.use(pinia).use(router).use(i18n).mount(host)
  await settle()
  return host
}

let pinia: ReturnType<typeof createPinia>

function input(host: HTMLElement, id: string, value: string) {
  const el = host.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!
  el.value = value
  el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }))
}

async function openTransactionForm(host: HTMLElement) {
  host.querySelector<HTMLButtonElement>('[aria-controls="tx-entry"]')!.click()
  await nextTick()
  input(host, 'symbol', 'AAPL')
  input(host, 'qty', '2')
  input(host, 'price', '100')
  input(host, 'fee', '3')
  await nextTick()
}

async function submit(host: HTMLElement) {
  host.querySelector('form.entry-line')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  await settle()
}

beforeEach(() => {
  localStorage.clear()
  pinia = createPinia()
  setActivePinia(pinia)
  i18n.global.locale.value = 'en'
  const auth = useAuthStore()
  auth.user = { userId: 1, email: 'demo@finlab.app', role: 'USER', token: 'session' }
  auth.token = 'session'
  usePortfolioStore().currentPortfolio = {
    id: 1, userId: 1, name: 'Test', baseCurrency: 'USD',
    createdAt: '2026-10-28T00:00:00Z', updatedAt: '2026-10-28T00:00:00Z'
  }
  vi.spyOn(useGoalPlanStore(), 'fetchGoals').mockResolvedValue()
  vi.spyOn(useGoalPlanStore(), 'fetchPlans').mockResolvedValue()
  vi.spyOn(useTransactionStore(), 'fetchTransactions').mockResolvedValue()
  vi.spyOn(useAnalyticsStore(), 'fetchAnalytics').mockResolvedValue()
})

afterEach(() => {
  app?.unmount()
  app = undefined
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

describe('goal and plan forms', () => {
  it('accepts the default amounts and the four-decimal positive monetary boundary', async () => {
    const host = await mount(GoalsPlansPage, '/goals-plans')
    host.querySelector<HTMLButtonElement>('[aria-controls="goal-form"]')!.click()
    host.querySelector<HTMLButtonElement>('[aria-controls="plan-form"]')!.click()
    await nextTick()
    for (const [id, value] of [['goal-target', '10000'], ['plan-amount', '500']]) {
      const el = host.querySelector<HTMLInputElement>(`#${id}`)!
      expect(el.value).toBe(value)
      expect(el.min).toBe('0.0001')
      expect(el.step).toBe('0.0001')
      expect(new Decimal(el.value).minus(el.min).mod(el.step).isZero()).toBe(true)
      el.value = '0.0001'
      expect(el.checkValidity()).toBe(true)
      el.value = '0'
      expect(el.checkValidity()).toBe(false)
    }
    const current = host.querySelector<HTMLInputElement>('#goal-current')!
    expect(current.min).toBe('0')
    expect(current.step).toBe('0.0001')
    expect(current.checkValidity()).toBe(true)
  })

  it('does not replace the session-expiry login destination with dashboard after portfolio loading', async () => {
    usePortfolioStore().currentPortfolio = null
    vi.spyOn(usePortfolioStore(), 'fetchPortfolios').mockImplementation(async () => {
      const original = router.currentRoute.value.fullPath
      useAuthStore().logout()
      await router.replace({ path: '/login', query: { redirect: original } })
    })
    await mount(GoalsPlansPage, '/goals-plans?view=plans#active')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/goals-plans?view=plans#active')
  })
})

describe('transaction amounts and submissions', () => {
  it('shows gross BUY cost and net SELL proceeds in the form and ledger', async () => {
    useTransactionStore().transactions = [
      { id: 1, portfolioId: 1, type: 'BUY', symbol: 'AAPL', qty: 2, price: 100, fee: 3, timestamp: '2026-10-27T00:00:00Z' },
      { id: 2, portfolioId: 1, type: 'SELL', symbol: 'AAPL', qty: 2, price: 100, fee: 3, timestamp: '2026-10-28T00:00:00Z' }
    ]
    const host = await mount(TransactionsPage, '/transactions')
    const rows = [...host.querySelectorAll('tbody tr')]
    expect(rows[0]!.querySelector('[data-label="Total"]')!.textContent).toContain('$197.00')
    expect(rows[1]!.querySelector('[data-label="Total"]')!.textContent).toContain('$203.00')
    await openTransactionForm(host)
    expect(host.querySelector('#transaction-total')!.textContent).toContain('$203.00')
    input(host, 'type', 'SELL')
    await nextTick()
    expect(host.querySelector('#transaction-total')!.textContent).toContain('$197.00')
  })

  it('reuses the live submit key after failure and rotates it for edited or successful new submissions', async () => {
    useConfigStore().setBackendMode('live')
    const create = vi.spyOn(apiClient, 'createTransaction').mockResolvedValue({
      success: false, error: { code: 'NETWORK_ERROR', message: 'Network unavailable' }
    })
    const host = await mount(TransactionsPage, '/transactions')
    await openTransactionForm(host)
    await submit(host)
    await submit(host)
    const first = create.mock.calls[0]![2]
    expect(first).toMatch(/^[0-9a-f]{8}-[0-9a-f-]{27}$/)
    expect(create.mock.calls[1]![2]).toBe(first)
    input(host, 'qty', '3')
    await nextTick()
    await submit(host)
    const edited = create.mock.calls[2]![2]
    expect(edited).not.toBe(first)
    create.mockResolvedValueOnce({ success: true, data: {
      id: 1, portfolioId: 1, type: 'BUY', symbol: 'AAPL', qty: 3, price: 100, fee: 3, timestamp: '2026-10-28T00:00:00Z'
    } })
    await submit(host)
    expect(create.mock.calls[3]![2]).toBe(edited)
    await openTransactionForm(host)
    await submit(host)
    expect(create.mock.calls[4]![2]).not.toBe(edited)
  })

  it('leaves mock submissions free of idempotency keys', async () => {
    const create = vi.spyOn(apiClient, 'createTransaction').mockResolvedValue({
      success: false, error: { code: 'VALIDATION_ERROR', message: 'Try again' }
    })
    const host = await mount(TransactionsPage, '/transactions')
    await openTransactionForm(host)
    await submit(host)
    expect(create.mock.calls[0]![2]).toBeUndefined()
  })
})
