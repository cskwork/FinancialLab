# FinLab architecture

FinLab is a static Vue 3 single-page app. In the public demo every "backend" call is served inside the browser by
`MockApiClient`, which persists to IndexedDB through Dexie and computes holdings and metrics locally. A `live` mode
exists that sends the same calls to an external REST server, but **no server implementation lives in this
repository** (see [Backend status](#backend-status)).

## Components

| Layer | Path | Responsibility |
|-------|------|----------------|
| Entry / startup | `frontend/src/main.ts` | Restores session and config from `localStorage`, awaits `initializeSeedData()` before mounting the router, shows a localized startup error screen if storage or seeding fails. |
| Routing | `frontend/src/app/router.ts` | Lazy-loaded routes (`/dashboard`, `/transactions`, `/goals-plans`, `/backtest`, `/api-playground`, `/docs`). Guard redirects unauthenticated users to `/login` and waits for `whenSeedReady()`. |
| Pages | `frontend/src/pages/*.vue` | UI. `BacktestLabPage` and `TransactionsPage` start workers directly. |
| State | `frontend/src/stores/*.ts` (Pinia) | `authStore`, `configStore` (backend mode, URL, locale), `portfolioStore`, `transactionStore`, `goalPlanStore`, `analyticsStore`, `toastStore`. Stores call only `apiClient`. |
| API boundary | `frontend/src/services/api/apiClient.ts` | `ApiClientInterface`; each method branches on `configStore.isMockMode`. Live calls go through `liveRequest()`. |
| API contract | `frontend/src/services/api/schemas.ts`, `frontend/public/docs/openapi.json` | Zod request/response schemas; OpenAPI description of the `/v1/*` endpoints. |
| Mock backend | `frontend/src/services/api/mockClient.ts` | Implements the contract over IndexedDB: validation, FIFO ledger checks, holdings, metrics, goals, plans, market data. |
| Persistence | `frontend/src/services/db/schema.ts`, `frontend/src/services/db/repositories/` | Dexie database `FinLabDB` (schema v1) with tables `users`, `portfolios`, `transactions`, `holdings`, `goals`, `plans`, `candles` (compound index `[symbol+date]`), `metricSnapshots`. One repository class per aggregate. |
| Domain logic | `frontend/src/utils/fifoCalculator.ts` | FIFO lot matching with `decimal.js`; throws on incomplete BUY/SELL rows and on overselling. |
| Seeding | `frontend/src/utils/seedLoader.ts` | Loads OHLCV CSVs from `frontend/public/data/ohlcv/` and the bundled `sample-transactions.json`; idempotent and non-destructive. |
| Workers | `frontend/src/workers/backtestWorker.ts`, `metricsWorker.ts`, `csvImportWorker.ts` | DCA vs lump-sum simulation; volatility, Sharpe, max drawdown; CSV parsing with PapaParse + `utils/csvImport.ts`. |

## Data flow

### Adding a transaction (mock mode)

1. `TransactionsPage` → `transactionStore` → `apiClient.createTransaction()`.
2. `apiClient` sees mock mode and calls `MockApiClient.createTransaction()`.
3. The request is parsed with `CreateTransactionRequestSchema` (Zod). A `ZodError` becomes `{ success: false, error: { code: 'VALIDATION_ERROR', details } }`.
4. For a SELL, the whole existing ledger plus the new row is replayed through `calculateHoldingsFromTransactions()`. If FIFO throws (oversell), the write is rejected with `VALIDATION_ERROR`.
5. `TransactionRepository.create()` writes to IndexedDB; the stored row is read back and returned through `TransactionResponseSchema.parse()`.

Deletion runs the same replay without the deleted row and refuses the delete if a later SELL would break, unless the
ledger was already broken (so pre-validation data can still be repaired).

### Dashboard metrics

1. `analyticsStore.fetchAnalytics()` gets holdings via `apiClient.getHoldings()` (FIFO holdings valued at the last stored close).
2. In mock mode it builds market-value-weighted daily returns from IndexedDB candles and posts them to `metricsWorker`.
3. The worker returns annualized volatility (`stdDev * sqrt(252)`), annualized Sharpe, and max drawdown; the store composes a `MetricSnapshotResponse`.
4. In live mode the store calls `apiClient.getPortfolioMetrics()` instead and trusts the server's calculation.

### Backtest

`BacktestLabPage` reads candles for the chosen symbol and date range from IndexedDB, requires at least 10 candles,
and posts them to `backtestWorker`. The worker returns both strategies and a comparison; `utils/equityCurve.ts`
turns the trades into the equity chart.

### Startup and seeding

`main.ts` awaits `initializeSeedData()` (one shared promise). It fills `candles` if the table is empty and ensures a
portfolio named `FinLab 샘플 포트폴리오 (2024)` with the 10 sample transactions, two goals and two plans exists for
`demo@finlab.app`. See [ADR 0003](adr/0003-idempotent-non-destructive-demo-seeding.md).

## Backend status

The repository contains **no backend source** — no Java, Gradle, or Maven files on any branch or in history
(`git log --all -- '*.java' 'pom.xml' 'build.gradle*'` returns nothing). `PRD.md` planned a Spring Boot 3 service
(Spring Security JWT, JPA, Flyway, springdoc) under `backend-samples/spring-boot`, and the CI step to build it is
still commented out in `.github/workflows/deploy.yml`.

What the frontend already fixes as the contract a backend would have to honor:

- **Endpoints:** `POST /v1/auth/login`; `GET/POST /v1/portfolios`, `GET/PATCH/DELETE /v1/portfolios/{id}`;
  `GET/POST /v1/portfolios/{id}/transactions`, `DELETE /v1/transactions/{id}`;
  `GET /v1/portfolios/{id}/holdings`; `GET /v1/metrics/portfolio/{id}`; goals, plans and market-data endpoints
  (full list in `apiClient.ts`, OpenAPI subset in `frontend/public/docs/openapi.json`).
- **Auth:** `Authorization: Bearer <token>` from the login response.
- **Tracing:** every live request sends a fresh `X-Request-Id` (`crypto.randomUUID()`).
- **Response envelope:** `liveRequest()` accepts either `{ data: ... }` or a bare body, then validates it with the
  same Zod schema the mock uses.
- **Error mapping on the client:** non-2xx → `HTTP_<status>` with the response text; schema mismatch →
  `SCHEMA_VALIDATION_FAILED`; network failure → `NETWORK_ERROR`; empty base URL → `BACKEND_URL_NOT_CONFIGURED`.
  Callers always receive `ApiResponse<T>` and never a thrown exception.
- **Business rules a server must reproduce:** FIFO cost basis, oversell rejection, and refusal to delete a BUY that
  a later SELL depends on are currently enforced only in `mockClient.ts`.

The default live URL in `configStore.ts` is `https://finlab-api.fly.dev`; whether a server runs there is unknown.
The public Vercel build sets `VITE_DEMO_ONLY=true`, which ignores any stored `live` setting, and its CSP blocks
cross-origin `connect-src`, so the public demo never calls a backend.

## External dependencies

Runtime (from `frontend/package.json`): Vue 3, Vue Router 4, Pinia 3, vue-i18n 9, Dexie 4, Zod 4, decimal.js,
PapaParse, Chart.js 4, lucide-vue-next, Fontsource fonts. Test: Vitest 3, happy-dom, fake-indexeddb.
Hosting: Vercel (`vercel.json`); a GitHub Pages workflow also exists (`.github/workflows/deploy.yml`).
No network APIs are called in mock mode; sample prices are static files in `frontend/public/data/`.

## Failure handling

| Failure | Handling | Where |
|---------|----------|-------|
| `localStorage` unreadable at startup | Startup error screen in the stored or default locale; app is not mounted. | `main.ts` |
| Seeding fails | Logged and startup error screen shown. Route guard's `whenSeedReady()` swallows the error so navigation is not blocked twice. | `main.ts`, `seedLoader.ts` |
| `localStorage` unavailable for the seed flag | Ignored; seeding re-checks IndexedDB rows on the next visit. | `seedLoader.ts` |
| Invalid request data | Zod → `VALIDATION_ERROR` with flattened details. | `mockClient.ts` |
| Oversell / dependent delete | Rejected before write with `VALIDATION_ERROR`. | `mockClient.ts` |
| Unexpected exception in mock | Caught, returned as `INTERNAL_ERROR`. | `mockClient.ts` |
| Live HTTP / network / schema errors | Mapped to `HTTP_<status>`, `NETWORK_ERROR`, `SCHEMA_VALIDATION_FAILED`. | `apiClient.ts` |
| Worker computation error | Worker catches and posts `{ type: 'error', error }`; caller rejects its promise and terminates the worker. | `workers/*.ts`, `analyticsStore.ts`, `BacktestLabPage.vue` |
| Too little price data for a backtest | Warning toast when fewer than 10 candles. | `BacktestLabPage.vue` |
| Component render error | `ErrorBoundary` component. | `components/layout/ErrorBoundary.vue` |

## Known gaps

- DCA intervals step by candle count (7 / 30 rows), not calendar weeks or months (`backtestWorker.ts`).
- Sharpe subtracts a decimal risk-free rate from percent returns, so the risk-free rate counts 100× too little
  (`metricsWorker.ts`, `mockClient.ts`).
- Metric functions are duplicated between `metricsWorker.ts` and `mockClient.ts`.
- `composables/useBacktest.ts` is not used by any page.
- Transaction fees are stored but not included in FIFO cost basis.
