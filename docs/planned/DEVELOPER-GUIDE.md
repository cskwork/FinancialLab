> **Superseded (2026-10-28).** This is a pre-implementation plan kept for history. Current documentation:
> [architecture](../architecture.md), [backend README](../../backend/README.md), [HTTP contract](../../contract/README.md),
> [ADRs](../adr/README.md).
>
> The implemented structure is described in [docs/architecture.md](../architecture.md) (frontend and backend
> packages, layering, transactions). The backend lives in `backend/`, not `backend-samples/spring-boot`.

# FinLab 개발자 가이드

## 목차

1. [개발 환경 설정](#개발-환경-설정)
2. [프로젝트 구조](#프로젝트-구조)
3. [아키텍처 개요](#아키텍처-개요)
4. [핵심 컴포넌트](#핵심-컴포넌트)
5. [데이터베이스 스키마](#데이터베이스-스키마)
6. [API 개발](#api-개발)
7. [상태 관리](#상태-관리)
8. [Web Workers](#web-workers)
9. [테스트 작성](#테스트-작성)
10. [빌드 및 배포](#빌드-및-배포)
11. [코드 스타일 가이드](#코드-스타일-가이드)

---

## 개발 환경 설정

### 사전 요구사항

```bash
# Node.js 버전 확인
node --version  # v20+ 권장, v16+ 지원

# npm 버전 확인
npm --version   # v8+
```

### 초기 설정

```bash
# 저장소 클론
git clone https://github.com/yourusername/finlab.git
cd finlab

# 프론트엔드 의존성 설치
cd frontend
npm install

# 개발 서버 실행
npm run dev

# 별도 터미널에서 테스트 실행
npm run test -- --watch
```

### IDE 설정

**VS Code (권장)**

필수 확장:
- Volar (Vue Language Features)
- TypeScript Vue Plugin (Volar)
- ESLint
- Prettier

권장 설정 (`.vscode/settings.json`):
```json
{
  "editor.formatOnSave": true,
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": true
  },
  "typescript.tsdk": "node_modules/typescript/lib"
}
```

**WebStorm**

- Vue.js 플러그인 활성화
- TypeScript 지원 활성화
- ESLint 자동 수정 활성화

---

## 프로젝트 구조

```
frontend/
├── public/                      # 정적 파일
│   ├── data/
│   │   ├── ohlcv/              # CSV 시세 데이터
│   │   └── seeds/              # 시드 데이터 JSON
│   └── docs/                   # 생성된 문서
│
├── src/
│   ├── app/                    # 애플리케이션 코어
│   │   ├── main.ts            # 엔트리 포인트
│   │   └── router.ts          # Vue Router 설정
│   │
│   ├── pages/                  # 페이지 컴포넌트
│   │   ├── LandingPage.vue
│   │   ├── LoginPage.vue
│   │   ├── DashboardPage.vue
│   │   ├── TransactionsPage.vue
│   │   ├── GoalsPlansPage.vue
│   │   ├── BacktestLabPage.vue
│   │   ├── ApiPlaygroundPage.vue
│   │   └── DocsPage.vue
│   │
│   ├── components/             # 재사용 컴포넌트
│   │   ├── Card.vue
│   │   ├── LoadingSpinner.vue
│   │   ├── ToastContainer.vue
│   │   ├── ErrorBoundary.vue
│   │   └── NavBar.vue
│   │
│   ├── stores/                 # Pinia 스토어
│   │   ├── authStore.ts
│   │   ├── portfolioStore.ts
│   │   ├── transactionStore.ts
│   │   ├── toastStore.ts
│   │   └── configStore.ts
│   │
│   ├── services/
│   │   ├── api/               # API 레이어
│   │   │   ├── index.ts       # API 클라이언트
│   │   │   ├── apiClient.ts   # HTTP 클라이언트
│   │   │   ├── mockClient.ts  # Mock 핸들러
│   │   │   └── schemas.ts     # Zod 스키마
│   │   │
│   │   └── db/                # IndexedDB 레이어
│   │       ├── schema.ts      # Dexie 스키마
│   │       ├── migrations/    # DB 마이그레이션
│   │       └── repositories/  # 리포지토리 패턴
│   │           ├── UserRepository.ts
│   │           ├── PortfolioRepository.ts
│   │           ├── TransactionRepository.ts
│   │           ├── GoalRepository.ts
│   │           ├── PlanRepository.ts
│   │           └── MetricSnapshotRepository.ts
│   │
│   ├── workers/               # Web Workers
│   │   ├── backtestWorker.ts
│   │   ├── metricsWorker.ts
│   │   └── csvImportWorker.ts
│   │
│   ├── utils/                 # 유틸리티
│   │   ├── fifoCalculator.ts
│   │   └── seedLoader.ts
│   │
│   ├── composables/           # Vue Composables
│   │   └── useToast.ts
│   │
│   ├── styles/                # 스타일
│   │   ├── tokens.css         # CSS 변수
│   │   └── global.css
│   │
│   ├── test/                  # 테스트 설정
│   │   └── setup.ts
│   │
│   ├── App.vue                # 루트 컴포넌트
│   ├── main.ts                # 진입점
│   └── env.d.ts               # 타입 선언
│
├── index.html
├── vite.config.ts             # Vite 설정
├── tsconfig.json              # TypeScript 설정
└── package.json
```

### 디렉토리 규칙

**pages/**
- 라우트에 매핑되는 페이지 컴포넌트
- `*Page.vue` 네이밍 컨벤션
- Lazy loading으로 로드

**components/**
- 재사용 가능한 UI 컴포넌트
- 단일 책임 원칙
- Props와 Events로 통신

**stores/**
- Pinia 스토어만 포함
- 도메인별로 분리
- Composition API 스타일 사용

**services/**
- 비즈니스 로직과 외부 의존성 분리
- API 클라이언트, DB 리포지토리

**workers/**
- CPU 집약적 작업 처리
- UI 블로킹 방지

---

## 아키텍처 개요

### 레이어 아키텍처

```
┌─────────────────────────────────────┐
│          Presentation Layer         │  Vue Components, Pages
├─────────────────────────────────────┤
│         Application Layer           │  Pinia Stores, Composables
├─────────────────────────────────────┤
│           Service Layer             │  API Client, Repositories
├─────────────────────────────────────┤
│        Infrastructure Layer         │  Dexie, Fetch, Workers
└─────────────────────────────────────┘
```

### 데이터 흐름

**읽기 흐름**
```
Component → Store → Repository → IndexedDB
                ↓
             Cache
```

**쓰기 흐름**
```
Component → Store → API Client → Mock/Live Backend
                                       ↓
                                  Repository → IndexedDB
```

### Backend 모드 토글

```typescript
// configStore.ts
export const useConfigStore = defineStore('config', () => {
  const backendMode = ref<'mock' | 'live'>('mock')
  const backendUrl = ref<string>('')

  function setBackendMode(mode: 'mock' | 'live') {
    backendMode.value = mode
    localStorage.setItem('BACKEND_MODE', mode)
  }

  return { backendMode, backendUrl, setBackendMode }
})

// API Client에서 사용
const configStore = useConfigStore()
if (configStore.backendMode === 'mock') {
  return mockClient.handle(request)
} else {
  return fetch(`${configStore.backendUrl}${path}`, options)
}
```

---

## 핵심 컴포넌트

### 1. API Client

**위치**: `src/services/api/index.ts`

```typescript
import { useConfigStore } from '@/stores/configStore'
import { mockClient } from './mockClient'

export async function apiRequest<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  const configStore = useConfigStore()
  const authStore = useAuthStore()

  // Mock 모드
  if (configStore.backendMode === 'mock') {
    return mockClient.handle<T>(path, options)
  }

  // Live 모드
  const url = `${configStore.backendUrl}${path}`
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${authStore.token}`,
      ...options?.headers
    }
  })

  if (!response.ok) {
    throw new ApiError(response.status, await response.json())
  }

  return response.json()
}
```

### 2. Dexie Repository 패턴

**예시**: `src/services/db/repositories/PortfolioRepository.ts`

```typescript
import { db, type Portfolio } from '../schema'

export class PortfolioRepository {
  /**
   * 사용자의 모든 포트폴리오 조회
   */
  async findByUserId(userId: number): Promise<Portfolio[]> {
    return db.portfolios
      .where('userId')
      .equals(userId)
      .toArray()
  }

  /**
   * 포트폴리오 생성
   */
  async create(data: Omit<Portfolio, 'id'>): Promise<number> {
    return db.portfolios.add(data)
  }

  /**
   * 포트폴리오 수정
   */
  async update(id: number, data: Partial<Portfolio>): Promise<number> {
    return db.portfolios.update(id, {
      ...data,
      updatedAt: new Date()
    })
  }

  /**
   * 포트폴리오 삭제 (관련 데이터 Cascade)
   */
  async delete(id: number): Promise<void> {
    await db.transaction('rw', db.portfolios, db.transactions, db.holdings, async () => {
      await db.portfolios.delete(id)
      await db.transactions.where('portfolioId').equals(id).delete()
      await db.holdings.where('portfolioId').equals(id).delete()
    })
  }
}

export const portfolioRepo = new PortfolioRepository()
```

### 3. Pinia Store

**예시**: `src/stores/portfolioStore.ts`

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { portfolioRepo } from '@/services/db/repositories'
import type { Portfolio } from '@/services/db/schema'

export const usePortfolioStore = defineStore('portfolio', () => {
  // State
  const portfolios = ref<Portfolio[]>([])
  const currentPortfolio = ref<Portfolio | null>(null)
  const loading = ref(false)

  // Getters
  const totalValue = computed(() => {
    return portfolios.value.reduce((sum, p) => sum + calculateValue(p), 0)
  })

  // Actions
  async function fetchPortfolios(userId: number) {
    loading.value = true
    try {
      portfolios.value = await portfolioRepo.findByUserId(userId)
    } finally {
      loading.value = false
    }
  }

  async function createPortfolio(data: Omit<Portfolio, 'id'>) {
    const id = await portfolioRepo.create(data)
    await fetchPortfolios(data.userId)
    return id
  }

  return {
    portfolios,
    currentPortfolio,
    loading,
    totalValue,
    fetchPortfolios,
    createPortfolio
  }
})
```

### 4. Zod 스키마 검증

**위치**: `src/services/api/schemas.ts`

```typescript
import { z } from 'zod'

// 스키마 정의
export const CreatePortfolioRequestSchema = z.object({
  name: z.string().min(1).max(100),
  baseCurrency: z.string().length(3)
})

// 타입 추론
export type CreatePortfolioRequest = z.infer<typeof CreatePortfolioRequestSchema>

// 사용 예시
function validatePortfolioData(data: unknown): CreatePortfolioRequest {
  const result = CreatePortfolioRequestSchema.safeParse(data)

  if (!result.success) {
    throw new ValidationError(result.error.errors)
  }

  return result.data
}
```

---

## 데이터베이스 스키마

### Dexie 스키마 정의

**위치**: `src/services/db/schema.ts`

```typescript
import Dexie, { type EntityTable } from 'dexie'

export interface Portfolio {
  id?: number
  userId: number
  name: string
  baseCurrency: string
  createdAt: Date
  updatedAt: Date
}

export class FinLabDatabase extends Dexie {
  portfolios!: EntityTable<Portfolio, 'id'>

  constructor() {
    super('FinLabDB')

    this.version(1).stores({
      portfolios: '++id, userId, name'
    })
  }
}

export const db = new FinLabDatabase()
```

### 마이그레이션

**새 버전 추가**

```typescript
// src/services/db/migrations/v2.ts
this.version(2).stores({
  // 기존 스키마 유지
  portfolios: '++id, userId, name',
  // 새 테이블 추가
  alerts: '++id, userId, portfolioId, type, threshold'
}).upgrade(tx => {
  // 데이터 마이그레이션 로직
  return tx.table('portfolios').toCollection().modify(portfolio => {
    portfolio.alertsEnabled = false
  })
})
```

**주의사항**
- 절대 이전 버전의 스키마를 삭제하지 마세요
- `upgrade` 함수로 데이터 마이그레이션
- 버전은 순차적으로 증가

---

## API 개발

### Mock API 핸들러 추가

**위치**: `src/services/api/mockClient.ts`

```typescript
import { z } from 'zod'
import { portfolioRepo } from '@/services/db/repositories'
import { CreatePortfolioRequestSchema } from './schemas'

class MockClient {
  async handle<T>(path: string, options?: RequestInit): Promise<T> {
    const method = options?.method || 'GET'

    // 라우팅
    if (method === 'POST' && path === '/v1/portfolios') {
      return this.createPortfolio(options) as Promise<T>
    }

    throw new Error(`Mock handler not found: ${method} ${path}`)
  }

  private async createPortfolio(options?: RequestInit) {
    // 요청 파싱
    const body = JSON.parse(options?.body as string)

    // 검증
    const validated = CreatePortfolioRequestSchema.parse(body)

    // 비즈니스 로직
    const userId = this.getCurrentUserId() // From JWT
    const id = await portfolioRepo.create({
      userId,
      ...validated,
      createdAt: new Date(),
      updatedAt: new Date()
    })

    // 응답
    return {
      id,
      userId,
      ...validated,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }
  }
}

export const mockClient = new MockClient()
```

### Rate Limiting 시뮬레이션

```typescript
class RateLimiter {
  private tokens = new Map<string, number>()
  private readonly maxTokens = 60
  private readonly refillRate = 60 / 60 // 60 requests per minute

  async checkLimit(userId: string): Promise<boolean> {
    const current = this.tokens.get(userId) || this.maxTokens

    if (current < 1) {
      throw new ApiError(429, {
        code: 'RATE_LIMIT_EXCEEDED',
        message: 'Too many requests'
      })
    }

    this.tokens.set(userId, current - 1)

    // Refill (간단한 구현)
    setTimeout(() => {
      this.tokens.set(userId, Math.min(this.maxTokens, current + this.refillRate))
    }, 1000)

    return true
  }
}
```

---

## 상태 관리

### Store 작성 패턴

**Composition API 스타일 (권장)**

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useExampleStore = defineStore('example', () => {
  // State
  const count = ref(0)
  const name = ref('Example')

  // Getters
  const doubleCount = computed(() => count.value * 2)

  // Actions
  function increment() {
    count.value++
  }

  async function fetchData() {
    // 비동기 로직
  }

  return {
    // State
    count,
    name,
    // Getters
    doubleCount,
    // Actions
    increment,
    fetchData
  }
})
```

### Store간 통신

```typescript
export const useTransactionStore = defineStore('transaction', () => {
  const portfolioStore = usePortfolioStore()

  async function addTransaction(data: Transaction) {
    const txId = await transactionRepo.create(data)

    // 다른 스토어 업데이트 트리거
    await portfolioStore.recalculateHoldings(data.portfolioId)

    return txId
  }

  return { addTransaction }
})
```

---

## Web Workers

### Worker 생성

**위치**: `src/workers/backtestWorker.ts`

```typescript
// Worker 코드
self.onmessage = async (e: MessageEvent) => {
  const { strategy, symbol, startDate, endDate, amount, interval } = e.data

  try {
    // CPU 집약적 계산
    const result = await runBacktest({
      strategy,
      symbol,
      startDate,
      endDate,
      amount,
      interval
    })

    // 결과 전송
    self.postMessage({ success: true, data: result })
  } catch (error) {
    self.postMessage({ success: false, error: error.message })
  }
}

// 백테스트 로직
async function runBacktest(params: BacktestParams) {
  const candles = await loadCandles(params.symbol, params.startDate, params.endDate)

  let shares = 0
  let totalInvested = 0

  // DCA 시뮬레이션
  for (const candle of candles) {
    if (shouldInvest(candle.date, params.interval)) {
      const sharesToBuy = params.amount / candle.close
      shares += sharesToBuy
      totalInvested += params.amount
    }
  }

  const finalValue = shares * candles[candles.length - 1].close

  return {
    finalValue,
    totalInvested,
    profit: finalValue - totalInvested,
    // ... 기타 지표
  }
}
```

### Worker 사용

```typescript
// 컴포넌트에서 사용
import BacktestWorker from '@/workers/backtestWorker?worker'

export function useBacktest() {
  const result = ref(null)
  const loading = ref(false)

  async function runBacktest(params: BacktestParams) {
    loading.value = true

    const worker = new BacktestWorker()

    worker.onmessage = (e: MessageEvent) => {
      if (e.data.success) {
        result.value = e.data.data
      } else {
        console.error(e.data.error)
      }
      loading.value = false
      worker.terminate()
    }

    worker.postMessage(params)
  }

  return { result, loading, runBacktest }
}
```

---

## 테스트 작성

### 단위 테스트

**위치**: `src/utils/fifoCalculator.spec.ts`

```typescript
import { describe, it, expect } from 'vitest'
import { calculateFIFO } from './fifoCalculator'

describe('FIFO Calculator', () => {
  it('should calculate average cost correctly', () => {
    const transactions = [
      { type: 'BUY', qty: 10, price: 100 },
      { type: 'BUY', qty: 5, price: 110 }
    ]

    const result = calculateFIFO(transactions)

    expect(result.shares).toBe(15)
    expect(result.avgCost).toBeCloseTo(103.33, 2)
  })

  it('should handle SELL transactions with FIFO', () => {
    const transactions = [
      { type: 'BUY', qty: 10, price: 100 },
      { type: 'BUY', qty: 5, price: 110 },
      { type: 'SELL', qty: 8, price: 120 }
    ]

    const result = calculateFIFO(transactions)

    expect(result.shares).toBe(7)
    expect(result.realizedPnL).toBeCloseTo(160, 2) // (120-100) * 8
  })

  it('should throw error when selling more than owned', () => {
    const transactions = [
      { type: 'BUY', qty: 5, price: 100 },
      { type: 'SELL', qty: 10, price: 120 }
    ]

    expect(() => calculateFIFO(transactions)).toThrow('Insufficient shares')
  })
})
```

### Pinia Store 테스트

```typescript
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePortfolioStore } from '@/stores/portfolioStore'

describe('Portfolio Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should fetch portfolios', async () => {
    const store = usePortfolioStore()

    await store.fetchPortfolios(1)

    expect(store.portfolios.length).toBeGreaterThan(0)
  })

  it('should create portfolio', async () => {
    const store = usePortfolioStore()

    const id = await store.createPortfolio({
      userId: 1,
      name: 'Test Portfolio',
      baseCurrency: 'USD',
      createdAt: new Date(),
      updatedAt: new Date()
    })

    expect(id).toBeTypeOf('number')
    expect(store.portfolios).toContainEqual(
      expect.objectContaining({ name: 'Test Portfolio' })
    )
  })
})
```

### 컴포넌트 테스트

```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PortfolioCard from '@/components/PortfolioCard.vue'

describe('PortfolioCard', () => {
  it('renders portfolio name', () => {
    const wrapper = mount(PortfolioCard, {
      props: {
        portfolio: {
          id: 1,
          name: 'My Portfolio',
          baseCurrency: 'USD',
          totalValue: 10000
        }
      }
    })

    expect(wrapper.text()).toContain('My Portfolio')
  })

  it('emits select event on click', async () => {
    const wrapper = mount(PortfolioCard, {
      props: { /* ... */ }
    })

    await wrapper.trigger('click')

    expect(wrapper.emitted('select')).toBeTruthy()
  })
})
```

---

## 빌드 및 배포

### 프로덕션 빌드

```bash
# 빌드
npm run build

# 결과물 확인
ls -la frontend/dist/

# 로컬에서 프로덕션 빌드 테스트
npm run preview
```

### Vite 설정

**위치**: `vite.config.ts`

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },

  build: {
    // 청크 사이즈 경고 임계값
    chunkSizeWarningLimit: 1000,

    // 코드 스플리팅
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor': ['vue', 'vue-router', 'pinia'],
          'charts': ['chart.js'],
          'db': ['dexie']
        }
      }
    }
  },

  // GitHub Pages 배포 시
  base: '/finlab/'
})
```

### GitHub Actions

**위치**: `.github/workflows/deploy.yml`

```yaml
name: Build and Deploy

on:
  push:
    branches: [main]

jobs:
  build-deploy:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: 'frontend/package-lock.json'

      - name: Install Dependencies
        working-directory: frontend
        run: npm ci

      - name: Run Tests
        working-directory: frontend
        run: npm run test

      - name: Build
        working-directory: frontend
        run: npm run build

      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./frontend/dist
```

---

## 코드 스타일 가이드

### TypeScript

```typescript
// ✅ Good: 명시적 타입
interface Portfolio {
  id: number
  name: string
}

function getPortfolio(id: number): Promise<Portfolio> {
  return apiRequest(`/portfolios/${id}`)
}

// ❌ Bad: any 사용
function getPortfolio(id: any): Promise<any> {
  return apiRequest(`/portfolios/${id}`)
}
```

### Vue 컴포넌트

```vue
<script setup lang="ts">
// ✅ Good: Composition API, TypeScript
import { ref, computed } from 'vue'

interface Props {
  portfolio: Portfolio
}

const props = defineProps<Props>()
const emit = defineEmits<{
  select: [id: number]
}>()

const formattedValue = computed(() => {
  return `$${props.portfolio.totalValue.toFixed(2)}`
})
</script>

<template>
  <div @click="emit('select', portfolio.id)">
    <h2>{{ portfolio.name }}</h2>
    <p>{{ formattedValue }}</p>
  </div>
</template>
```

### 네이밍 컨벤션

**파일명**
- 컴포넌트: PascalCase (`PortfolioCard.vue`)
- 유틸리티: camelCase (`fifoCalculator.ts`)
- 스토어: camelCase + Store 접미사 (`portfolioStore.ts`)

**변수명**
- 상수: UPPER_SNAKE_CASE
- 변수/함수: camelCase
- 타입/인터페이스: PascalCase
- Private: `_` 접두사

### 주석 (한글)

```typescript
/**
 * FIFO 방식으로 평균 단가 계산
 * @param transactions 거래 목록
 * @returns 평균 단가 및 보유 수량
 */
export function calculateFIFO(transactions: Transaction[]): FIFOResult {
  // 매수 큐 초기화
  const buyQueue: BuyLot[] = []

  for (const tx of transactions) {
    if (tx.type === 'BUY') {
      // 매수분을 큐에 추가
      buyQueue.push({ qty: tx.qty, price: tx.price })
    } else if (tx.type === 'SELL') {
      // 가장 오래된 매수분부터 소진
      let remaining = tx.qty
      while (remaining > 0 && buyQueue.length > 0) {
        // ...
      }
    }
  }

  return calculateAverage(buyQueue)
}
```

### Commit 메시지

```
feat: 새 포트폴리오 생성 기능 추가
fix: FIFO 계산 버그 수정
docs: API 문서 업데이트
refactor: Store 구조 개선
test: 백테스트 Worker 테스트 추가
chore: 의존성 업데이트
```

---

## 기여 가이드라인

1. **이슈 생성**: 기능 요청이나 버그 리포트 먼저 생성
2. **브랜치 생성**: `feature/기능명` 또는 `fix/버그명`
3. **코딩**: 스타일 가이드 준수
4. **테스트**: 변경 사항에 대한 테스트 작성
5. **커밋**: 명확한 커밋 메시지
6. **Pull Request**: 템플릿에 따라 PR 생성
7. **코드 리뷰**: 피드백 반영
8. **머지**: Squash and merge

---

## 추가 리소스

- [Vue 3 문서](https://vuejs.org/)
- [Pinia 문서](https://pinia.vuejs.org/)
- [Vite 문서](https://vitejs.dev/)
- [Dexie.js 문서](https://dexie.org/)
- [Zod 문서](https://zod.dev/)
- [Chart.js 문서](https://www.chartjs.org/)

---

**설정 및 배포에 대한 자세한 내용은 [Configuration Guide](CONFIGURATION-GUIDE.md)를 참조하세요.**
