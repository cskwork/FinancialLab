# 0001. Serve the API contract from an in-browser mock backend, with a live switch

- Status: Accepted (as implemented)
- Evidence: `frontend/src/services/api/apiClient.ts`, `frontend/src/services/api/mockClient.ts`,
  `frontend/src/services/api/schemas.ts`, `frontend/src/stores/configStore.ts`, `vercel.json`,
  commit `0df3a84` (2026-10-10, demo-only startup), `PRD.md` ("Backend mode toggle").

## Context

The product goal in `PRD.md` is a static SPA that can be hosted for free and opened by anyone, while still showing a
realistic client/server split. A planned Spring Boot service was never added to the repository, and the public demo
must work with no server, no account, and no network calls for data.

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
- Business rules (FIFO, oversell rejection) currently live only in the mock; a real backend must reimplement them
  and nothing yet checks that both sides agree.
- `apiClient.ts` repeats the mock/live branch in every method (18 endpoints), which is verbose but keeps each call
  explicit.
- Errors never throw across the boundary; every caller handles `success: false`.

## Alternatives considered

- **Pages talk to Dexie directly.** Simpler, but removes the API seam and the ability to point the same UI at a
  server. Not chosen.
- **HTTP-level mocking (e.g. a service-worker mock).** Would exercise real `fetch` calls, but adds a dependency and
  a second place to define behavior. No such tool is present in the repo.
