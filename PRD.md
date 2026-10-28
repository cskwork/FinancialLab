# FinLab — Production-Ready Static App (Vue 3 + GitHub Pages)

**Refined & Reorganized Plan**

---

## 🎯 **Vision**

**Audience:** Senior engineers, technical recruiters  
**Goal:** Static SPA demonstrating full-stack fintech competency—simulated backend (portfolios, backtests, risk metrics), production UX, and Java/Spring Boot evidence (OpenAPI/Javadoc/Coverage)  
**Non-goal:** Real money, live trading, or backend hosting (optional)

---

## 📐 **Architecture Decisions**

### Frontend Stack
- **Framework:** Vue 3 + Vite + TypeScript (Composition API)
- **State:** Pinia (official Vue store)
- **Router:** Vue Router 4
- **Storage:** IndexedDB via Dexie.js
- **Mock API:** Client-side router with OpenAPI-conformant handlers
- **Workers:** Dedicated workers for backtests, metrics, CSV parsing
- **Charts:** Chart.js or ECharts (bundled, no CDN)
- **UI:** Headless UI (Radix Vue) or shadcn-vue for primitives
- **Validation:** Zod (schema-first, generates TS types)

### Backend Evidence (Java)
- **Stack:** Spring Boot 3 + Spring Security (JWT) + JPA + Flyway
- **Docs:** springdoc-openapi, Javadoc, JaCoCo, Testcontainers
- **Deployment:** Optional (Render/Fly/Railway for "Live Mode")

### Quality Bar
- ✅ WCAG AA accessibility
- ✅ Lighthouse: Perf ≥90, A11y ≥90, Best Practices ≥90
- ✅ Deterministic financial calculations (unit tested)
- ✅ Zero console errors in core flows
- ✅ Offline-first (optional PWA)

---

## 🗄️ **Data Model**

```
User
├─ id (PK), email (UNIQUE), role (USER|ADMIN), createdAt

Portfolio
├─ id (PK), userId (FK), name, baseCurrency, createdAt, updatedAt

Transaction
├─ id (PK), portfolioId (FK), type (BUY|SELL|DEPOSIT|WITHDRAW)
├─ symbol?, qty?, price?, fee (default 0), timestamp, note?

Holding (computed)
├─ portfolioId+symbol → shares, avgCost, marketValue, unrealizedPnL

Goal
├─ id (PK), portfolioId (FK), name, targetAmount, targetDate, currentAmount

Plan
├─ id (PK), portfolioId (FK), symbol, amountPerInterval, 
├─ interval (WEEKLY|MONTHLY), startDate, endDate?

Candle
├─ symbol+date (PK), open, high, low, close, volume

MetricSnapshot
├─ id (PK), portfolioId (FK), asOf, totalValue, cash,
├─ pnlDaily, pnlYTD, volatility, sharpe, maxDrawdown
```

**Storage:** IndexedDB with versioned migrations (Dexie schema evolution)

---

## 🔌 **API Contract (OpenAPI 3.1)**

### Core Endpoints

**Auth**
```
POST   /v1/auth/login              → { token, user }
```

**Portfolios**
```
GET    /v1/portfolios              → Portfolio[]
POST   /v1/portfolios              → Portfolio
GET    /v1/portfolios/{id}         → Portfolio
PATCH  /v1/portfolios/{id}         → Portfolio
DELETE /v1/portfolios/{id}         → 204
```

**Transactions**
```
GET    /v1/portfolios/{id}/transactions       → Transaction[]
POST   /v1/portfolios/{id}/transactions       → Transaction
POST   /v1/portfolios/{id}/transactions/bulk  → { created: number }
```

**Holdings & Metrics**
```
GET    /v1/portfolios/{id}/holdings  → Holding[]
GET    /v1/metrics/portfolio/{id}    → MetricSnapshot
```

**Goals & Plans**
```
GET    /v1/portfolios/{id}/goals  → Goal[]
POST   /v1/portfolios/{id}/goals  → Goal
GET    /v1/portfolios/{id}/plans  → Plan[]
POST   /v1/portfolios/{id}/plans  → Plan
```

**Backtesting**
```
POST   /v1/backtests/dca  → { strategy, finalValue, cagr, sharpe, maxDrawdown }
```

**Market Data**
```
GET    /v1/market/symbols             → Symbol[]
GET    /v1/market/candles             → Candle[] (query: symbol, from, to)
```

### Standard Headers
```
Authorization: Bearer <jwt>
X-Request-Id: <uuid>
```

### Error Format
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid quantity",
  "details": { "field": "qty", "constraint": "min" }
}
```

**Action:** Generate `openapi.json` from Spring Boot (springdoc) → commit to `/frontend/public/docs/`

---

## 🧮 **Financial Logic (Deterministic)**

### FIFO Average Cost
```
Maintain queue of purchase lots
BUY  → append { qty, price, fee }
SELL → consume from head, compute realized gain/loss
avgCost = Σ(qty × price + fee) / Σ(qty)
```

### Risk Metrics (Annualized)
```
Daily Returns:     r_t = (V_t - V_{t-1}) / V_{t-1}
Volatility:        σ = std(r) × √252
Sharpe Ratio:      SR = (μ - r_f) / σ  (r_f = 2%)
Max Drawdown:      MDD = max((peak - trough) / peak)
```

### DCA Backtest
```
Strategy: Invest fixed amount per interval at close price
Allow fractional shares
Compare vs. lump-sum at start date
```

**Unit Tests Required:**
- Holdings recalculation (idempotent from transactions)
- SELL validation (no negative shares)
- DCA vs Lump-sum on known dataset
- Max drawdown on controlled peaks/troughs

---

## 📁 **Project Structure**

```
/
├─ frontend/
│  ├─ public/
│  │  ├─ docs/
│  │  │  ├─ openapi.json           # Generated from Spring
│  │  │  ├─ apidocs/               # Javadoc HTML
│  │  │  ├─ coverage/              # JaCoCo reports
│  │  │  ├─ tests/                 # Surefire/Failsafe
│  │  │  ├─ diagrams/              # ERD, sequences, deployment
│  │  │  └─ snippets/              # Curated Java code samples
│  │  ├─ data/
│  │  │  ├─ ohlcv/                 # AAPL.csv, SPY.csv
│  │  │  └─ seeds/                 # sample-transactions.json
│  │  ├─ robots.txt
│  │  ├─ sitemap.xml
│  │  └─ manifest.webmanifest
│  │
│  ├─ src/
│  │  ├─ app/
│  │  │  ├─ App.vue               # Root component
│  │  │  ├─ router.ts             # Vue Router config
│  │  │  └─ main.ts               # Bootstrap + Pinia
│  │  │
│  │  ├─ pages/
│  │  │  ├─ LandingPage.vue
│  │  │  ├─ LoginPage.vue
│  │  │  ├─ DashboardPage.vue
│  │  │  ├─ TransactionsPage.vue
│  │  │  ├─ GoalsPlansPage.vue
│  │  │  ├─ BacktestLabPage.vue
│  │  │  ├─ ApiPlaygroundPage.vue  # Swagger UI embed
│  │  │  └─ DocsPage.vue
│  │  │
│  │  ├─ components/
│  │  │  ├─ common/               # Button, Input, Toast
│  │  │  ├─ tables/               # DataTable, TransactionRow
│  │  │  ├─ charts/               # PortfolioChart, BacktestChart
│  │  │  └─ forms/                # TransactionForm, GoalForm
│  │  │
│  │  ├─ stores/
│  │  │  ├─ auth.ts               # Pinia store
│  │  │  ├─ portfolio.ts
│  │  │  ├─ backtest.ts
│  │  │  └─ config.ts             # Backend mode toggle
│  │  │
│  │  ├─ services/
│  │  │  ├─ api-client.ts         # HTTP wrapper (mock/live toggle)
│  │  │  ├─ mock-api/
│  │  │  │  ├─ router.ts          # Route matching
│  │  │  │  ├─ handlers/          # auth.ts, portfolios.ts...
│  │  │  │  ├─ validators.ts      # Zod schemas
│  │  │  │  ├─ rbac.ts            # Role checks
│  │  │  │  └─ rate-limit.ts      # Token bucket simulation
│  │  │  └─ db/
│  │  │     ├─ schema.ts          # Dexie tables
│  │  │     ├─ migrations/        # v1.ts, v2.ts...
│  │  │     └─ repositories/      # UserRepo, PortfolioRepo...
│  │  │
│  │  ├─ workers/
│  │  │  ├─ backtest.worker.ts
│  │  │  ├─ metrics.worker.ts
│  │  │  └─ csv-parser.worker.ts
│  │  │
│  │  ├─ utils/
│  │  │  ├─ finance.ts            # FIFO, metrics, DCA
│  │  │  ├─ money.ts              # Decimal.js wrapper
│  │  │  ├─ dates.ts              # UTC normalization
│  │  │  └─ csv.ts                # Streaming parser
│  │  │
│  │  ├─ composables/             # Vue composables
│  │  │  ├─ usePortfolio.ts
│  │  │  ├─ useBacktest.ts
│  │  │  └─ useToast.ts
│  │  │
│  │  └─ styles/
│  │     ├─ tokens.css            # Design system variables
│  │     └─ global.css
│  │
│  ├─ index.html
│  ├─ vite.config.ts
│  ├─ package.json
│  └─ tsconfig.json
│
├─ backend-samples/
│  └─ spring-boot/
│     ├─ src/
│     │  ├─ main/
│     │  │  ├─ java/.../
│     │  │  │  ├─ entity/
│     │  │  │  ├─ repository/
│     │  │  │  ├─ service/
│     │  │  │  ├─ controller/
│     │  │  │  ├─ config/         # OpenAPI, CORS, JWT
│     │  │  │  └─ FinLabApplication.java
│     │  │  └─ resources/
│     │  │     ├─ db/migration/   # Flyway V1__init.sql
│     │  │     └─ application.yml
│     │  └─ test/
│     │     ├─ java/.../
│     │     │  ├─ unit/
│     │     │  ├─ integration/    # Testcontainers
│     │     │  └─ contract/       # OpenAPI validation
│     ├─ pom.xml
│     └─ Dockerfile
│
├─ .github/
│  └─ workflows/
│     └─ deploy.yml
│
└─ README.md
```

---

## 🎨 **Frontend Architecture**

### 1. Backend Mode Toggle

**Storage:**
```typescript
// localStorage keys
BACKEND_MODE: 'mock' | 'live'
BACKEND_BASE_URL: string (e.g., 'http://localhost:8080')
```

**API Client Logic:**
```typescript
// services/api-client.ts
const baseURL = mode === 'mock' 
  ? '' 
  : localStorage.getItem('BACKEND_BASE_URL')

if (mode === 'mock') {
  return mockRouter.handle(request)  // Local handlers
} else {
  return fetch(baseURL + path, {
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Request-Id': crypto.randomUUID()
    }
  })
}
```

### 2. Mock API Quality Features

- ✅ **Schema Validation:** Zod schemas matching OpenAPI
- ✅ **RBAC:** Role-based guards (401/403 responses)
- ✅ **Rate Limiting:** Token bucket per endpoint → 429 with `Retry-After`
- ✅ **Fault Injection:** Toggle for 5xx/latency simulation
- ✅ **Versioning:** `/v1` namespace with deprecation headers

### 3. Web Workers

**Backtest Worker:**
```typescript
// Input: { strategy, symbol, startDate, endDate, amount, interval }
// Output: { finalValue, cagr, volatility, sharpe, maxDrawdown }
// Handles: 10k+ candles without UI freeze
```

**Metrics Worker:**
```typescript
// Input: { portfolioId, transactions, candles }
// Output: { dailyReturns, volatility, sharpe, maxDrawdown }
// Runs: Async on portfolio update
```

**CSV Parser Worker:**
```typescript
// Input: File blob
// Output: Streaming parsed rows
// Uses: PapaParse with chunk processing
```

### 4. State Management (Pinia)

**Stores:**
```typescript
// stores/auth.ts
export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(null)
  const isAuthenticated = computed(() => !!token.value)
  
  async function login(email: string, password: string) {
    // Mock JWT generation or live API call
  }
  
  return { user, token, isAuthenticated, login }
})

// stores/config.ts
export const useConfigStore = defineStore('config', () => {
  const backendMode = ref<'mock' | 'live'>('mock')
  const backendUrl = ref<string>('')
  
  return { backendMode, backendUrl }
})
```

### 5. Error Handling

**Global Error Boundary:**
```vue
<!-- App.vue -->
<template>
  <ErrorBoundary>
    <RouterView />
  </ErrorBoundary>
</template>
```

**Toast Notifications:**
```typescript
// composables/useToast.ts
export function useToast() {
  const { addToast } = useToastStore()
  
  function error(message: string, retry?: () => void) {
    addToast({ type: 'error', message, action: retry })
  }
  
  return { error, success, warning, info }
}
```

---

## 🔒 **Production Hardening**

### Security

**Content Security Policy:**
```html
<meta http-equiv="Content-Security-Policy" 
      content="default-src 'self'; 
               img-src 'self' data:; 
               script-src 'self' 'unsafe-eval' 'unsafe-inline'; 
               style-src 'self' 'unsafe-inline';
               worker-src 'self' blob:; 
               connect-src 'self' https://*;">
```

**JWT Simulation:**
- Clearly labeled "DEMO MODE - NOT SECURE"
- Never imply real authentication
- Admin role unlocks seed data controls only

### Performance

- ✅ Route-based code splitting (`defineAsyncComponent`)
- ✅ Lazy load Swagger UI and heavy charts
- ✅ Vite auto-generates content-hashed filenames
- ✅ Large CSVs loaded on-demand (not in initial bundle)
- ✅ Use `<Suspense>` for async components

### Accessibility

- ✅ Semantic HTML5 (`<nav>`, `<main>`, `<section>`)
- ✅ ARIA labels on interactive elements
- ✅ Focus management in modals/dialogs
- ✅ Keyboard navigation (tab order, shortcuts)
- ✅ Color contrast WCAG AA (4.5:1)

### SEO & PWA

**Static Assets:**
```xml
<!-- robots.txt -->
User-agent: *
Allow: /

<!-- sitemap.xml -->
<urlset>
  <url><loc>https://you.github.io/finlab/</loc></url>
  <url><loc>https://you.github.io/finlab/docs/</loc></url>
  ...
</urlset>
```

**PWA (Optional):**
```json
// manifest.webmanifest
{
  "name": "FinLab",
  "short_name": "FinLab",
  "start_url": "/",
  "display": "standalone",
  "icons": [...]
}
```

### Observability

**Client-side Logging:**
```typescript
// utils/logger.ts
export const logger = {
  debug: (msg, meta) => console.log('[DEBUG]', msg, meta),
  error: (msg, err) => {
    console.error('[ERROR]', msg, err)
    // Optional: send to Sentry
  }
}

// Log panel in dev tools
// Filter by: level, route, duration
```

---

## ☕ **Java Backend (Evidence Pipeline)**

### Build & Test

**Dependencies (pom.xml):**
```xml
spring-boot-starter-web
spring-boot-starter-security
spring-boot-starter-data-jpa
spring-boot-starter-validation
flyway-core
postgresql
springdoc-openapi-starter-webmvc-ui
testcontainers
jacoco-maven-plugin
```

**Test Strategy:**
- **Unit:** Services, domain logic
- **Integration:** Repositories (Testcontainers Postgres)
- **Contract:** OpenAPI schema validation

### CI Artifact Generation

**Outputs:**
1. `openapi.json` → `/frontend/public/docs/`
2. `target/site/apidocs/` → `/frontend/public/docs/apidocs/`
3. `target/site/jacoco/` → `/frontend/public/docs/coverage/`
4. `surefire-reports/` → `/frontend/public/docs/tests/`

**Optional:**
- JAR artifact → GitHub Releases
- Docker image → GitHub Container Registry

---

## 🚀 **CI/CD Pipeline (GitHub Actions)**

```yaml
# .github/workflows/deploy.yml
name: Build and Deploy

on:
  push:
    branches: [main]

jobs:
  build-deploy:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pages: write
      id-token: write
    
    steps:
      # 1. Checkout
      - uses: actions/checkout@v4
      
      # 2. Build Spring Boot (docs only)
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'
      
      - name: Build Spring & Generate Docs
        working-directory: backend-samples/spring-boot
        run: |
          mvn -B clean verify
          mkdir -p ../../frontend/public/docs/{apidocs,coverage,tests}
          
          # OpenAPI
          cp target/openapi.json ../../frontend/public/docs/
          
          # Javadoc
          cp -r target/site/apidocs/* ../../frontend/public/docs/apidocs/ || true
          
          # JaCoCo Coverage
          cp -r target/site/jacoco/* ../../frontend/public/docs/coverage/ || true
          
          # Test Reports
          cp -r target/surefire-reports ../../frontend/public/docs/tests/ || true
      
      # 3. Build Vue App
      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: 'frontend/package-lock.json'
      
      - name: Install Dependencies
        working-directory: frontend
        run: npm ci
      
      - name: Build SPA
        working-directory: frontend
        run: npm run build
      
      # 4. Deploy to GitHub Pages
      - name: Setup Pages
        uses: actions/configure-pages@v5
      
      - name: Upload Artifact
        uses: actions/upload-pages-artifact@v3
        with:
          path: frontend/dist
      
      - name: Deploy to Pages
        uses: actions/deploy-pages@v4
```

---

## ✅ **Acceptance Criteria**

### Functional Tests

| Test | Expected |
|------|----------|
| Create portfolio → add transactions → refresh | Data persists in IndexedDB |
| Holdings P/L calculation | Matches expected on seeded data |
| Backtest 10k OHLCV rows | Returns deterministic results in <1s |
| Swagger UI loads | Exercises ≥3 endpoints (mock mode) |
| Switch to Live URL | Sends real HTTP (visible in Network tab) |

### Non-Functional Tests

| Metric | Target |
|--------|--------|
| Lighthouse Performance | ≥90 |
| Lighthouse Accessibility | ≥90 |
| Lighthouse Best Practices | ≥90 |
| Console Errors (core flows) | 0 |
| CSP Violations | 0 |
| Offline Support (PWA) | Core pages work |

---

## 📝 **Implementation Roadmap**

### Phase 1: Foundation (Week 1)
1. ✅ Initialize Vite + Vue 3 + TypeScript + Pinia
2. ✅ Setup Vue Router with lazy loading
3. ✅ Create design tokens (CSS variables)
4. ✅ Configure Dexie (IndexedDB schema + migrations)
5. ✅ Implement seed data loader

### Phase 2: Core Features (Week 2)
6. ✅ Mock API router (auth, portfolios, transactions)
7. ✅ FIFO holdings calculation + unit tests
8. ✅ Portfolio CRUD pages
9. ✅ Transaction import (CSV worker)
10. ✅ Basic charts (Portfolio value over time)

### Phase 3: Advanced Features (Week 3)
11. ✅ Backtest worker (DCA vs Lump-sum)
12. ✅ Metrics worker (Sharpe, volatility, drawdown)
13. ✅ Goals & Plans pages
14. ✅ Risk metrics dashboard

### Phase 4: Documentation & Polish (Week 4)
15. ✅ Swagger UI integration (`/api-playground`)
16. ✅ Backend toggle (mock ↔ live)
17. ✅ Java backend build in CI
18. ✅ Docs pages (OpenAPI, Javadoc, Coverage)
19. ✅ Error boundary + toast system
20. ✅ A11y audit + fixes

### Phase 5: Launch Prep
21. ✅ CSP + security headers
22. ✅ Lighthouse optimizations
23. ✅ README + demo video
24. ✅ Landing page polish
25. ✅ Final smoke tests

---

## 🎬 **Landing Page (Recruiter View)**

### Hero Section
- **90-second demo video/GIF**
- **Tagline:** "Full-stack fintech simulation: Vue 3 frontend + Java Spring Boot backend"

### CTA Buttons
```
[🚀 Try API Playground] [📐 System Design] [☕ Java Docs & Coverage]
```

### Mode Switcher
```
Backend Mode: [Mock ▼] [Live]
Live URL: [http://localhost:8080] [Connect]
```

### Feature Highlights
- 📊 Portfolio tracking with FIFO cost basis
- 🧪 DCA backtesting engine (10k+ candles)
- 📈 Risk metrics (Sharpe, volatility, max drawdown)
- 🔌 OpenAPI contract with mock/live toggle
- ☕ Spring Boot evidence (tests, coverage, docs)

### Footer Note
> "Educational simulation. No real money. Java backend optional ([backend setup guide](backend/README.md))."

---

## ⚠️ **Risk Mitigation**

| Risk | Mitigation |
|------|------------|
| Large CSV parse blocks UI | Parse in Worker with streaming (PapaParse chunks) |
| Time zone drift in candles | Normalize to UTC, store as date strings (YYYY-MM-DD) |
| Floating-point precision loss | Use Decimal.js for money calculations |
| GitHub Pages cache staleness | Vite auto-generates content hashes for cache busting |
| CORS with Live URL | Configure `@Bean CorsConfigurationSource` in Spring |
| JWT expiry handling | Auto-refresh or redirect to login |

---

## 🎁 **Deliverables Checklist**

### Code
- [ ] Vue 3 app with 5 core pages
- [ ] IndexedDB repos with migrations
- [ ] 3 Web Workers (backtest, metrics, CSV)
- [ ] Mock API with RBAC + rate limiting
- [ ] Spring Boot backend (optional but encouraged)

### Documentation
- [ ] OpenAPI spec (`/docs/openapi.json`)
- [ ] Javadoc (`/docs/apidocs/`)
- [ ] JaCoCo coverage (`/docs/coverage/`)
- [ ] System diagrams (ERD, sequence)
- [ ] README with setup + demo steps

### Tests
- [ ] 15+ unit tests (financial logic)
- [ ] Integration tests (Spring + Testcontainers)
- [ ] Lighthouse audit passing (≥90 all categories)

### Deployment
- [ ] GitHub Actions workflow
- [ ] Live GitHub Pages URL
- [ ] Optional: Live Spring Boot deployment

---

## 🚀 **Next Actions**

### Immediate (Day 1)
1. **Create repo:** `finlab-production`
2. **Copy CI workflow:** `.github/workflows/deploy.yml`
3. **Initialize frontend:** `npm create vite@latest frontend -- --template vue-ts`
4. **Install core deps:**
   ```bash
   npm install pinia vue-router dexie chart.js zod
   npm install -D @types/node
   ```

### Week 1 Sprint
5. **Setup Dexie schema** with User/Portfolio/Transaction tables
6. **Create mock auth handler** (JWT simulation)
7. **Build portfolio list page** (CRUD operations)
8. **Wire up IndexedDB repos**

### Optional: Generate Starter Files

I can provide:
- ✅ **Minimal OpenAPI spec** matching the endpoints above
- ✅ **Dexie schema** with migrations template
- ✅ **Backtest Worker** with test vectors
- ✅ **Vue composables** for portfolio/backtest logic
- ✅ **Pinia stores** boilerplate

---

## 💡 **Key Insight**

**The differentiator:** Most demos are toys. This one proves:
1. **Full-stack thinking** (frontend + backend contract)
2. **Production patterns** (workers, migrations, RBAC, observability)
3. **Financial domain expertise** (FIFO, Sharpe, backtesting)
4. **Java competency** (Spring Boot + tests + docs)

**The backend toggle** is the secret weapon—shows mock implementation *and* ability to integrate with real APIs.

---

**Ready to build? Start with the Dexie schema—everything flows from data.** 🚀