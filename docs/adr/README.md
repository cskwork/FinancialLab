# Architecture decision records

FinLab has two ADR series. Numbers restart per series, so links below name the series.

## Frontend and mock (`docs/adr/`)

| ADR | Decision | Status |
|-----|----------|--------|
| [0001](0001-mock-backend-behind-api-contract.md) | Serve the `/v1/*` contract from an in-browser mock, with a live switch | Accepted; amended 2026-10-28 for the real backend |
| [0002](0002-validate-fifo-ledger-before-write.md) | Validate the FIFO ledger before every write (mock) | Accepted; server counterpart is backend 0001 |
| [0003](0003-idempotent-non-destructive-demo-seeding.md) | Seed sample data idempotently without touching visitor data (IndexedDB) | Accepted; mock only |

## Backend (`backend/docs/adr/`)

| ADR | Decision | Status |
|-----|----------|--------|
| [backend 0001](../../backend/docs/adr/0001-ledger-consistency.md) | Lock each portfolio (`PESSIMISTIC_WRITE`) before replaying and mutating its FIFO ledger; optimistic-only locking and stored holdings rejected; idempotency keys checked under the same lock | Accepted, 2026-10-28 |
| [backend 0002](../../backend/docs/adr/0002-auth-contract.md) | Require a transient password, issue short-lived RS256 tokens, bound login with per-process buckets and a hashing semaphore | Accepted, 2026-10-28 |
| [backend 0003](../../backend/docs/adr/0003-contract-verification.md) | Pin the HTTP contract with shared fixtures checked by Zod and backend tests; test on Zonky embedded PostgreSQL, not H2 or Testcontainers (no Docker) | Accepted, 2026-10-28 |

Overview of how these fit together: [docs/architecture.md](../architecture.md).
