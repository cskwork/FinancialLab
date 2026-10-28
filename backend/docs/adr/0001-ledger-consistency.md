# 0001. Lock each portfolio before replaying and mutating its FIFO ledger

- Status: Accepted (implemented)
- Date: 2026-10-28
- Evidence: `transaction/application/TransactionService.java`, `transaction/domain/FifoLedger.java`,
  `portfolio/persistence/PortfolioLockingRepositoryImpl.java`, `transaction/application/IdempotencyPurge.java`,
  `integration/ApiIntegrationTest.java` under the backend
  main/test Java source roots.

## Context

The frontend rejects oversells by replaying transactions chronologically with FIFO lot matching. A server must
preserve this rule across concurrent requests: if two sells both read ten available shares, each can validate
a sale of six and together leave a broken ledger. A delete can also invalidate later sells if it removes their
required buy. Checking only the new transaction does not establish the complete ledger invariant.

## Decision

Create and delete transaction services run inside one database transaction and acquire a `PESSIMISTIC_WRITE`
lock on the owned portfolio before reading or validating its ledger. They replay the complete candidate ledger
in timestamp/ID order before committing. Other writers for that portfolio wait; unrelated portfolios have
different lock rows.

PostgreSQL's transaction-local `lock_timeout` and the JPA timeout hint share one validated setting, default
**3000 ms**. The custom locking repository uses `set_config(..., true)` so the database enforces the bound
for the current transaction even where a driver/provider ignores a positive JPA hint. Lock failures are
translated to **409**, preserving the ledger rather than silently weakening validation.

The pure `FifoLedger` uses `BigDecimal` with `DECIMAL128` division. Buy fees become per-share lot cost; sell fees
reduce proceeds. It rejects oversells with the frontend's validation messages. Deleting a buy needed by a later
sell is refused. The mock's limited repair behavior is preserved: deleting from an already-invalid legacy ledger
is allowed, while creating a transaction always validates the resulting complete ledger.

Optional idempotency keys are scoped to a portfolio, bound to a normalized request fingerprint, and checked
under the same lock. A live matching key replays its stored JSON response; a different intent returns **422**.
The response record and ledger write commit together. The default TTL is **24 hours**, configurable through
`finlab.idempotency.ttl`; equality with the expiry instant counts as expired. Expired records are removed on
key reuse and by a scheduled purge with a default **1-hour** fixed delay. Both use the injected UTC clock.

Transaction listing pushes optional filters and the default **1000-row** bound into SQL. Ledger validation
and valuation use a separate complete-ledger query, so limiting an HTTP collection does not change FIFO.

## Consequences

The invariant is enforced across service instances using PostgreSQL row locking, without an in-memory mutex.
FIFO replay is easy to compare with the frontend and costs linear work in ledger size for each mutation.
Writes within one portfolio serialize, so large or busy portfolios will need measured limits or a snapshot
strategy. `@Version` protects mutable portfolio updates against lost updates, but does not replace the ledger
lock: inserting ledger rows need not change the portfolio version. Goals and plans have create/read/delete
operations without an update operation, so their unused version counters were removed by V4.

The sample's **1622.75 / 738.25** figures are unrealized gains at the final close, not realized gains.
`FifoLedgerTest` and the frontend FIFO spec separately pin actual realized gains at **231.15 / 313.55** and
AAPL average FIFO cost at **195.0394444444**. Public quantity and average-cost numbers round to **8 decimal
places**, monetary values to **4**, with `HALF_UP` at the HTTP mapping boundary. Internal lots and valuation
retain full precision, so output formatting cannot change FIFO decisions or metric weights.

## Alternatives considered

- **Validate before an unlocked insert.** Two requests can validate the same shares. Rejected.
- **Use only optimistic locking.** Requires every ledger mutation to update the same aggregate version and
  retry the complete validation. The explicit row lock is simpler for the required invariant.
- **Maintain mutable holdings instead of replaying.** Faster reads, but backdated transactions and deletion
  require extra reconciliation logic. Deferred until a measured need exists.
