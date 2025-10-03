# Implementation Notes — 2025-10-04

## Summary
- Added global resilience layer (ErrorBoundary, toast system) and refreshed landing CTA experience.
- Introduced backend mode toggle with persistent config store and consolidated API client for mock/live switching.
- Extended mock API surface to cover holdings, metrics, goals, plans plus Dexie repositories and seeds.
- Wired Web Workers into UI flows (backtesting lab, risk analytics, CSV import) and exposed analytics via dashboard/ui stores.
- Published documentation assets (OpenAPI spec, docs hub, API playground) and refreshed README to reflect current capabilities.

## Decisions
- Built custom API playground to avoid CDN dependencies while surfacing the OpenAPI contract and mock execution path.
- Metrics are computed client-side via worker in mock mode, while live mode defers to remote API responses for parity.
- CSV uploads reuse incremental transaction creation to respect Pinia store side effects and analytics refresh.
- Placeholder docs in `public/docs` allow CI artifacts to drop in without breaking navigation.

## Tests
- `npm run test` (Vitest) — FIFO calculator specs.
- Manual smoke on dashboard/backtest/playground in mock mode (login → portfolio overview → CSV import → metrics refresh).

## Limitations / Follow-ups
- Live mode execution paths are thinly validated; requires integration against deployed Spring Boot service.
- Chart-based dashboards and Lighthouse optimisation remain outstanding (Phase 5 items).
- API playground currently maps a curated subset of operations; extend coverage as backend evolves.
