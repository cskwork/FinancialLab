# 0001. Serve the API contract from an in-browser mock backend, with a live switch

- Status: Accepted (as implemented). Amended 2026-10-28: the live side now has a real Spring Boot backend in
  `backend/`; see the [update](#update-2026-10-28-a-real-live-backend) below.
- Evidence: `frontend/src/services/api/apiClient.ts`, `frontend/src/services/api/mockClient.ts`,
  `frontend/src/services/api/schemas.ts`, `frontend/src/stores/configStore.ts`, `vercel.json`,
  commit `0df3a84` (2026-10-10, demo-only startup), `PRD.md` ("Backend mode toggle").

## Context

The product goal in `PRD.md` is a static SPA that can be hosted for free and opened by anyone, while still showing a
realistic client/server split. When this decision was made, the planned Spring Boot service did not exist, and the
public demo must work with no server, no account, and no network calls for data.

## Decision

- All stores call one `ApiClientInterface` (`apiClient.ts`). Each method checks `configStore.isMockMode` and either
  delegates to `MockApiClient` or sends the equivalent `/v1/*` HTTP request through `liveRequest()`.
- `MockApiClient` behaves like a server: it validates input with the Zod schemas in `schemas.ts`, enforces business
  rules, persists to IndexedDB through repositories, and returns the same `ApiResponse<T>` envelope
  (`{ success, data | error: { code, message, details? } }`).
- Live responses are parsed with the same Zod response schemas, so a server that drifts from the contract surfaces
  as `SCHEMA_VALIDATION_FAILED` instead of a runtime error deep in a page.
- The public build sets `VITE_DEMO_ONLY=true`; `configStore` then ignores any stored `live` mode. `vercel.json`'s
  CSP restricts `connect-src` to self and Vercel, which would block an external backend anyway.

## Consequences

- The whole app, including validation and error paths, runs and is tested without a server
  (`mockClient.spec.ts` uses `fake-indexeddb`).
- Business rules (FIFO, oversell rejection) had to be reimplemented by a real backend. That has now happened (see
  the update below); shared contract fixtures check response shapes on both sides, and both test suites pin the same
  reference values.
- `apiClient.ts` repeats the mock/live branch in every method (18 endpoints), which is verbose but keeps each call
  explicit.
- Metric arithmetic is shared with the worker through `frontend/src/utils/metrics.ts`; both convert the annual
  decimal risk-free rate to daily percent before calculating Sharpe.
- Pre-backend plans live in [planned/](../planned/), each marked as superseded.
- Errors never throw across the boundary; every caller handles `success: false`.

## Alternatives considered

- **Pages talk to Dexie directly.** Simpler, but removes the API seam and the ability to point the same UI at a
  server. Not chosen.
- **HTTP-level mocking (e.g. a service-worker mock).** Would exercise real `fetch` calls, but adds a dependency and
  a second place to define behavior. No such tool is present in the repo.

## Update 2026-10-28: a real live backend

The live branch of this design now points at `backend/`, a Spring Boot service on PostgreSQL that implements the 18
operations of `ApiClientInterface`. The mock stays the only mode of the public demo. What changed on the frontend
side:

- `POST /v1/auth/login` now takes email **and** password (`LoginRequestSchema`); the mock keeps one-click demo entry.
- `liveRequest()` parses `application/problem+json` errors with `ApiErrorSchema` and, on a 401 from a protected
  call, clears the session and routes to `/login`.
- `contract/*.json` examples are validated against the Zod schemas (`contract.spec.ts`) and against real backend
  responses (`ApiIntegrationTest.java`), which closes the "nothing checks that both sides agree" gap for shapes.

Backend decisions: [ledger locking](../../backend/docs/adr/0001-ledger-consistency.md),
[authentication](../../backend/docs/adr/0002-auth-contract.md),
[contract verification and Docker-free PostgreSQL tests](../../backend/docs/adr/0003-contract-verification.md).
Current overview: [docs/architecture.md](../architecture.md).
