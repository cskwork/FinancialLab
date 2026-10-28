# 0002. Validate the FIFO ledger before every write

- Status: Accepted (as implemented)
- Evidence: `frontend/src/services/api/mockClient.ts` (`findLedgerError`, `createTransaction`,
  `deleteTransaction`), `frontend/src/utils/fifoCalculator.ts`, `frontend/src/services/api/mockClient.spec.ts`,
  commit `d2d333d` (2026-09-26, "Fix demo ledger validation, CSV imports and local date defaults").

## Context

Holdings are not stored as truth; they are recomputed from the transaction ledger by replaying it through
`FifoCalculator` (oldest lot sold first). The calculator throws when a SELL exceeds the shares held. Before commit
`d2d333d`, an oversell or the deletion of a BUY that a later SELL depended on could be saved, after which every
holdings and analytics request for that portfolio failed.

## Decision

- On SELL creation, replay the existing ledger plus the new row with `calculateHoldingsFromTransactions()`. If it
  throws, reject the write with `VALIDATION_ERROR` and the calculator's message.
- On delete, replay the ledger without the target row. If that fails while the current ledger is valid, refuse the
  delete instead of cascading. If the ledger is already broken (data from before validation), allow the delete so the
  user can repair it.
- Money and quantity arithmetic inside the calculator uses `decimal.js` to avoid binary floating-point drift in cost
  basis.

## Consequences

- An invalid ledger cannot be created through the API, so holdings and metrics stay computable.
- Each SELL or delete replays the full portfolio ledger: O(n) per write. Fine for demo-sized portfolios; a server
  would want stored lots or an index.
- Buy fees are allocated per share in FIFO lots, so partial sales retain their share of cost. Sell fees reduce
  realized proceeds; `FifoCalculator.getSales()` exposes the resulting cost basis and realized PnL.
- Backtest simulations are separate from ledger accounting and still omit fees.

## Alternatives considered

- **Store holdings and update them incrementally.** Faster reads, but two sources of truth that can disagree.
- **Cascade-delete dependent SELLs.** Silently removes user data. Rejected in the code comment in `deleteTransaction`.
- **Validate only in the UI form.** CSV import and any future client would bypass it; the check sits in the API layer.
