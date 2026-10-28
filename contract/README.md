# Shared HTTP examples

The `/v1/*` contract between the Vue client and the Spring Boot backend. Context:
[top-level README](../README.md), [architecture](../docs/architecture.md),
[contract ADR](../backend/docs/adr/0003-contract-verification.md).

These JSON files pin response field names, JSON types, and frontend Zod constraints. IDs, timestamps and sample
values are examples, not a golden database dump. Backend domain/integration assertions separately pin the
hand-computed FIFO and metrics values from the bundled data.

[`frontend/src/services/api/contract.spec.ts`](../frontend/src/services/api/contract.spec.ts) checks every JSON file with its corresponding schema.
`backend/src/test/java/io/github/cskwork/finlab/integration/ApiIntegrationTest.java` checks real HTTP responses
against these same shapes. Optional trade fields are present in `transaction.json`; the collection example
uses only the required fields because a DEPOSIT/WITHDRAW can omit symbol, quantity and price.

The integration suite captures actual response bodies in `backend/target/contract-actual/`. CI runs the same
frontend spec against that directory through `FINLAB_CONTRACT_ACTUAL_DIR`, in addition to the committed
examples. To repeat that consumer check after backend `verify`:

```sh
cd frontend
FINLAB_CONTRACT_ACTUAL_DIR="$(pwd)/../backend/target/contract-actual" \
  npm test -- --run src/services/api/contract.spec.ts
```

| HTTP endpoint | Fixture |
| --- | --- |
| POST `/v1/auth/login` | `login.json` |
| GET `/v1/portfolios` | `portfolios.json` |
| POST `/v1/portfolios` | `portfolio.json` |
| GET `/v1/portfolios/{id}` | `portfolio.json` |
| PATCH `/v1/portfolios/{id}` | `portfolio.json` |
| DELETE `/v1/portfolios/{id}` | `delete.json` |
| GET `/v1/portfolios/{id}/transactions` | `transactions.json` |
| POST `/v1/portfolios/{id}/transactions` | `transaction.json` |
| DELETE `/v1/transactions/{id}` | `delete.json` |
| GET `/v1/portfolios/{id}/holdings` | `holdings.json` |
| GET `/v1/metrics/portfolio/{id}` | `metrics.json` |
| GET `/v1/portfolios/{id}/goals` | `goals.json` |
| POST `/v1/portfolios/{id}/goals` | `goal.json` |
| DELETE `/v1/goals/{id}` | `delete.json` |
| GET `/v1/portfolios/{id}/plans` | `plans.json` |
| POST `/v1/portfolios/{id}/plans` | `plan.json` |
| DELETE `/v1/plans/{id}` | `delete.json` |
| GET `/v1/market-data` | `market-data.json` |

The five `error-*.json` examples cover field validation (400), bearer authentication (401), role denial (403),
missing/foreign ownership (404), and idempotency payload mismatch (422). Errors use RFC 9457 ProblemDetail
with stable `urn:finlab:problem:<kebab-case-code>` types and additive `code`, `message`, and optional `details` fields; validation details contain `fieldErrors`.
The live client validates `application/problem+json` with `ApiErrorSchema` and retains its structured fields.
Unreadable/non-problem responses get a generic `HTTP_<status>` fallback. Protected-call 401 responses clear
the session and return to `/login?redirect=<original fullPath>`; wrong credentials remain a login-form error. Operational 409 conflicts,
429 login limits, and 503 hash saturation use the same problem format; the latter two include `Retry-After`.

The approved login request now requires `{ "email": "...", "password": "..." }`. Bcrypt credentials are limited
to 72 UTF-8 bytes on both sides. Only the mock client's `demo@finlab.app` account accepts the legacy email-only example used by the bundled API Playground.
It invents no password; supplied passwords and other mock accounts are validated. Live login validates the
required password before sending a request.

Successful creates return 201. Deletes return 200 with a JSON body, since the existing live client always calls
`response.json()` on success. Optional response properties are omitted rather than serialized as `null`.

Public numbers use JSON number types, not strings: monetary fields round to 4 decimal places, quantities and
average FIFO costs to 8, using `HALF_UP` and ordinary decimal output. Internal calculations keep full precision;
the unrounded Sharpe reference remains `5.4106641762`. Transaction list filtering occurs in SQL and returns
the newest 1000 matches in descending timestamp/ID order by default, with `X-Total-Count` for all filtered matches; full-ledger FIFO calculations do not use this HTTP list bound.

Observed test results and verification limits belong in
[the dated backend record](../backend/docs/verification-2026-10-28.md), not in these example values.
