import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App, type Component } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import i18n from '@/i18n'
import { db } from '@/services/db/schema'
import { goalRepository, planRepository, portfolioRepository, transactionRepository } from '@/services/db/repositories'
import { useAuthStore } from '@/stores/authStore'
import { useConfigStore } from '@/stores/configStore'
import { DEMO_EMAIL, DEMO_PORTFOLIO_NAME } from '@/utils/seedLoader'
import ko from '@/locales/ko.json'
import en from '@/locales/en.json'
import openapi from '../../public/docs/openapi.json'
import LandingPage from './LandingPage.vue'
import LoginPage from './LoginPage.vue'
import DashboardPage from './DashboardPage.vue'
import TransactionsPage from './TransactionsPage.vue'
import GoalsPlansPage from './GoalsPlansPage.vue'
import BacktestLabPage from './BacktestLabPage.vue'
import ApiPlaygroundPage from './ApiPlaygroundPage.vue'
import DocsPage from './DocsPage.vue'

type Locale = 'ko' | 'en'

const HANGUL = /[가-힣]/
let mounted: App | null = null
let missingKeys: string[] = []

async function settle() {
  for (let i = 0; i < 6; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
  }
}

async function render(page: Component, locale: Locale, setup?: () => Promise<void>) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }]
  })
  useConfigStore().setLocale(locale)
  i18n.global.locale.value = locale
  if (setup) await setup()

  const app = createApp(page)
  app.use(pinia).use(router).use(i18n)
  await router.push('/')
  const host = document.createElement('div')
  document.body.appendChild(host)
  app.mount(host)
  mounted = app
  await settle()
  return host
}

// 화면 문구 + 보조 기술용 속성. 언어 선택기의 '한국어' 항목(lang="ko")은 언어 이름이므로 제외한다.
function visibleCopy(host: HTMLElement): string {
  const clone = host.cloneNode(true) as HTMLElement
  clone.querySelectorAll('[lang="ko"]').forEach((el) => el.remove())
  const attrs = [...clone.querySelectorAll('[aria-label],[title],[placeholder],[alt]')].flatMap((el) =>
    ['aria-label', 'title', 'placeholder', 'alt'].map((name) => el.getAttribute(name) ?? '')
  )
  return [clone.textContent ?? '', ...attrs].join('\n')
}

// 시드와 이름·값이 같은 기록과 사용자가 직접 쓴 기록을 함께 만든다. 저장소만으로는 둘을 구분할 수 없다.
const STORED_GOAL_NAMES = ['Retirement 2045', '내 집 마련']

async function loginDemoWithSampleNamedRecords() {
  const auth = useAuthStore()
  expect(await auth.login(DEMO_EMAIL)).toBe(true)
  const portfolioId = await portfolioRepository.create({ userId: auth.currentUserId!, name: DEMO_PORTFOLIO_NAME, baseCurrency: 'USD' })
  await goalRepository.create({
    portfolioId,
    name: 'Retirement 2045',
    targetAmount: 250000,
    targetDate: new Date('2045-12-31'),
    currentAmount: 45000
  })
  await goalRepository.create({
    portfolioId,
    name: '내 집 마련',
    targetAmount: 80000,
    targetDate: new Date('2030-06-30'),
    currentAmount: 5000
  })
  await planRepository.create({
    portfolioId,
    symbol: 'SPY',
    amountPerInterval: 300,
    interval: 'WEEKLY',
    startDate: new Date('2024-02-01'),
    isActive: true
  })
  await transactionRepository.create({
    portfolioId,
    type: 'BUY',
    symbol: 'AAPL',
    qty: 50,
    price: 185.64,
    fee: 2.5,
    timestamp: new Date('2024-01-02T14:30:00.000Z'),
    note: '첫 AAPL 매수'
  })
}

const routes: Array<[string, Component, string, string]> = [
  ['landing', LandingPage, 'Open the demo portfolio', '데모 포트폴리오 열기'],
  ['login', LoginPage, 'Portfolio Management Demo', '포트폴리오 관리 데모'],
  ['dashboard', DashboardPage, 'Please login to view your portfolio', '포트폴리오를 보려면 로그인하세요'],
  ['transactions', TransactionsPage, 'Please login to view transactions', '거래 내역을 보려면 로그인하세요'],
  ['goals-plans', GoalsPlansPage, 'Log in to manage goals and plans.', '로그인 후 목표와 계획을 관리할 수 있습니다.'],
  ['backtest', BacktestLabPage, 'Run Backtest', '백테스트 실행'],
  ['api-playground', ApiPlaygroundPage, 'Select an endpoint to view details and execute it.', '엔드포인트를 선택하면'],
  ['docs', DocsPage, 'Documentation Hub', '문서 허브']
]

describe('route copy in both languages', () => {
  beforeEach(async () => {
    localStorage.clear()
    missingKeys = []
    i18n.global.setMissingHandler((_locale, key) => {
      missingKeys.push(key)
      return key
    })
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ paths: {} }))))
    vi.spyOn(console, 'error').mockImplementation(() => {})
    await Promise.all([db.users.clear(), db.portfolios.clear(), db.transactions.clear(), db.goals.clear(), db.plans.clear()])
  })

  afterEach(() => {
    mounted?.unmount()
    mounted = null
    document.body.innerHTML = ''
    i18n.global.setMissingHandler(null)
    i18n.global.locale.value = 'ko'
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it.each(routes)('%s renders English copy without Korean leftovers', async (_name, page, english) => {
    const copy = visibleCopy(await render(page, 'en'))
    expect(copy).toContain(english)
    expect(copy.match(new RegExp(`.{0,30}${HANGUL.source}.{0,30}`, 'g')) ?? []).toEqual([])
    expect(missingKeys).toEqual([])
  })

  it.each(routes)('%s renders Korean copy', async (_name, page, _english, korean) => {
    const copy = visibleCopy(await render(page, 'ko'))
    expect(copy).toContain(korean)
    expect(missingKeys).toEqual([])
  })

  it('offers the language selector on landing and login, where the nav is hidden', async () => {
    for (const page of [LandingPage, LoginPage]) {
      const host = await render(page, 'en')
      const select = host.querySelector<HTMLSelectElement>('select')!
      expect(host.querySelector(`label[for="${select.id}"]`)?.textContent).toBe('Display language')

      select.value = 'ko'
      select.dispatchEvent(new Event('change'))
      await settle()
      expect(useConfigStore().locale).toBe('ko')
      expect(localStorage.getItem('finlab_locale')).toBe('ko')
      expect(host.textContent).toContain('로그인')
      mounted?.unmount()
      mounted = null
    }
  })

  it('shows stored portfolio and goal names as written in both languages, even when they match sample names', async () => {
    const loginDemo = async () => {
      expect(await useAuthStore().login(DEMO_EMAIL)).toBe(true)
    }

    const goalsEnHost = await render(GoalsPlansPage, 'en', loginDemoWithSampleNamedRecords)
    const goalsEn = visibleCopy(goalsEnHost)
    expect([...goalsEnHost.querySelectorAll('h3')].map((el) => el.textContent)).toEqual(
      expect.arrayContaining(STORED_GOAL_NAMES)
    )
    expect(goalsEn).toContain('Weekly')
    expect(goalsEn).not.toContain('은퇴 자금')
    mounted?.unmount()

    const goalsKoHost = await render(GoalsPlansPage, 'ko', loginDemo)
    const goalsKo = visibleCopy(goalsKoHost)
    expect([...goalsKoHost.querySelectorAll('h3')].map((el) => el.textContent)).toEqual(
      expect.arrayContaining(STORED_GOAL_NAMES)
    )
    expect(goalsKo).toContain('매주')
    expect(goalsKo).not.toContain('은퇴 자금')
    mounted?.unmount()

    const ledgerEn = await render(TransactionsPage, 'en', loginDemo)
    const ledger = visibleCopy(ledgerEn)
    expect(ledger).toContain(DEMO_PORTFOLIO_NAME)
    expect(ledger).not.toContain('FinLab sample portfolio')
    expect(ledgerEn.querySelector('.tx-type')?.textContent).toBe('BUY')
    expect(ledgerEn.querySelector('.tx-note')?.textContent).toBe('첫 AAPL 매수')
    expect(ledgerEn.querySelector('.tx-date')?.textContent).toMatch(/Jan 2, 2024/)
    mounted?.unmount()

    const ledgerKo = await render(TransactionsPage, 'ko', loginDemo)
    expect(visibleCopy(ledgerKo)).toContain(DEMO_PORTFOLIO_NAME)
    expect(ledgerKo.querySelector('.tx-type')?.textContent).toBe('매수')

    expect((await db.portfolios.toArray()).map((portfolio) => portfolio.name)).toEqual([DEMO_PORTFOLIO_NAME])
    expect((await db.goals.toArray()).map((goal) => goal.name)).toEqual(STORED_GOAL_NAMES)
    expect(missingKeys).toEqual([])
  })

  it('names every bundled API operation in the current language and falls back to the spec summary', async () => {
    const unknown = { summary: 'Delete everything', operationId: 'purgeAll' }
    const spec = { ...openapi, paths: { ...openapi.paths, '/admin/purge': { delete: unknown } } }
    const bundled = Object.values(openapi.paths).flatMap((methods) =>
      Object.values(methods as Record<string, { summary: string; operationId: string }>)
    )
    expect(bundled).toHaveLength(11)

    const host = await render(ApiPlaygroundPage, 'ko', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(spec))))
    })
    const buttons = [...host.querySelectorAll<HTMLButtonElement>('button.endpoint')]
    expect(buttons).toHaveLength(12)
    const heading = () => host.querySelector('.details-header h2')?.textContent

    for (const [index, operation] of bundled.entries()) {
      buttons[index]!.click()
      await nextTick()
      const korean = (ko.playground.summaries as Record<string, string>)[operation.operationId]
      expect(korean).toMatch(HANGUL)
      expect(heading()).toBe(korean)
    }

    // 언어를 바꾸면 선택된 작업 이름도 바로 바뀐다
    i18n.global.locale.value = 'en'
    await nextTick()
    expect(heading()).toBe(en.playground.summaries.createPlan)

    buttons[11]!.click()
    await nextTick()
    expect(heading()).toBe(unknown.summary)
    i18n.global.locale.value = 'ko'
    await nextTick()
    expect(heading()).toBe(unknown.summary)

    // 원본 OpenAPI 명세의 summary는 영어 원문 그대로다
    expect(bundled.map((operation) => operation.summary)).toEqual(
      bundled.map((operation) => (en.playground.summaries as Record<string, string>)[operation.operationId])
    )
    expect(missingKeys).toEqual([])
  })

  it('describes the bundled sample candles without inflating their size', async () => {
    for (const locale of ['en', 'ko'] as const) {
      const copy = visibleCopy(await render(LandingPage, locale))
      expect(copy).not.toMatch(/10k|10,000|1만/)
      expect(copy).toContain('AAPL')
      expect(copy).toContain('OHLCV')
      mounted?.unmount()
      mounted = null
    }
  })
})
