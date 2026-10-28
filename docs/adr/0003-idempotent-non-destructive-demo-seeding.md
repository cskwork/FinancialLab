# 0003. Seed sample data idempotently, from real rows, without touching visitor data

- Status: Accepted (as implemented)
- Evidence: `frontend/src/utils/seedLoader.ts`, `frontend/src/utils/seedLoader.spec.ts`, `frontend/src/main.ts`,
  `frontend/src/app/router.ts`, commits `6b6f52f` (2026-09-24) and `0df3a84` (2026-10-10).

## Context

The demo is only useful if sample prices and a sample portfolio exist. Commit `6b6f52f` records the original bug:
Dexie's `populate` hook created `demo@finlab.app` with an empty portfolio, so an "is the database empty?" check
always skipped seeding and the demo showed no holdings, transactions, or backtest symbols. Visitors also keep their
own portfolios in the same IndexedDB, and several tabs can start at once.

## Decision

- Check each dataset separately against actual IndexedDB rows. A `localStorage` flag (`finlab_demo_seed_v1`) is
  written but never trusted as proof that rows exist.
- Load candles only when the `candles` table is empty, re-checking inside a Dexie `rw` transaction.
- Put sample transactions in a dedicated portfolio (`FinLab 샘플 포트폴리오 (2024)`). Reuse it, or any portfolio that
  already contains every sample row (matched by a content key), instead of creating duplicates. Never clear or edit
  other portfolios.
- Run user/portfolio/transaction/goal/plan seeding in one Dexie transaction, and share one in-flight promise
  (`initializeSeedData`) so `main.ts` and the router guard (`whenSeedReady`) wait on the same work.
- Bundle `sample-transactions.json` into the app so an already seeded demo reopens offline; OHLCV CSVs are fetched
  from `public/data/ohlcv/`.

## Consequences

- Restoring the sample after a visitor deleted it is safe to repeat and does not duplicate fixtures.
- App mount waits for seeding; a seeding failure shows a startup error screen rather than an empty dashboard.
- Changing the sample data requires care: content-key matching means edited fixtures create a new sample set
  instead of updating the old one.

## Alternatives considered

- **Seed in Dexie's `populate` hook only.** Runs once per database, so it cannot repair a deleted sample, and it was
  the source of the original bug.
- **Wipe and reseed on demand.** Simple, but destroys visitor data; the code explicitly avoids it.
