# 0003. Pin the Vue HTTP contract with shared fixtures and real PostgreSQL tests

- Status: Accepted (implemented)
- Date: 2026-10-28
- Evidence: repository-root `contract/*.json`, `frontend/src/services/api/contract.spec.ts`,
  `backend/src/test/java/io/github/cskwork/finlab/integration/ApiIntegrationTest.java`, `backend/pom.xml`,
  and `.github/workflows/ci.yml`.

## Context

The frontend already defines request paths, methods, headers, and Zod response shapes. Older API planning docs
are insufficient where they disagree with that shipped client. Independent frontend/backend test suites can
both pass while returning different JSON. Database behavior, especially row locking, also needs PostgreSQL
rather than a substitute database. Docker and Testcontainers are prohibited for this project.

## Decision

The current `apiClient.ts` and `schemas.ts` define the `/v1/*` contract, with the owner-approved login password
addition. Shared JSON examples pin every response family, including list responses, optional-field examples,
delete acknowledgments, and validation/security/idempotency problems. Vitest assigns every fixture a matching
Zod schema; the backend integration test checks corresponding real response field names and JSON types while
allowing generated IDs, timestamps, and compatible extra problem metadata to vary.

Resource creates return **201**, login and reads **200**. Deletes return **200** with `{"success":true}`, because
the client calls `response.json()` for every successful request. Optional absent fields are omitted rather than
serialized as null. RFC 9457 `ProblemDetail` adds `code`, `message`, and optional `details`, including field
errors. `ApiExceptionHandler` extends Spring's `ResponseEntityExceptionHandler` so framework error status and
headers are preserved instead of reimplementing each framework failure. The live client validates
`application/problem+json` against `ApiErrorSchema`, presents its message, and uses `HTTP_<status>` for
unreadable/non-problem responses. A protected-call **401** clears authentication and returns to login.

Response number formatting is explicit: **4 decimal places** for money and **8** for quantity/average cost,
using `HALF_UP`; Jackson writes ordinary JSON numbers without scientific notation. Rounding happens only in
HTTP mappings. Pure FIFO and metrics calculations retain full precision and their reference assertions.

Spring Boot integration tests start Zonky embedded PostgreSQL **2.2.2** directly and close it after the suite.
They exercise migrations, login, owned CRUD, fee-aware holdings/metrics, conflicting concurrent sells,
idempotent replay/expiry, and hidden foreign resources. Domain tests pin the same hand-computed values and
bundled input files as the frontend. MVC slices test boundary validation/errors/security; ArchUnit enforces
feature-layer dependency rules. Maven `verify` checks **80% minimum line coverage per application/domain
package**, and CI runs backend verification alongside the existing frontend tests/build. The integration
suite writes actual bodies to `backend/target/contract-actual/`. The backend CI job then sets
`FINLAB_CONTRACT_ACTUAL_DIR` and runs the frontend contract spec against those captured bodies, connecting
the real producer to the real Zod consumer. Surefire reports are uploaded on failure.

A separate local smoke uses Homebrew PostgreSQL and the running HTTP service through `scripts/smoke.sh`.
It redacts the token from its transcript. Exact results belong in the dated verification record, not in an
assumption that the presence of these tests means they passed.

## Consequences

Fixtures make response drift visible to both runtimes, and real PostgreSQL establishes the database semantics
the ledger depends on. Fixtures check shape, while dedicated assertions check arithmetic, ownership, and
concurrency; neither replaces the other. The tests need actual PostgreSQL binaries and a compatible host,
without a container runtime. Local smoke establishes one local runtime path, not public deployment readiness.

## Alternatives considered

- **Generate backend code from the older OpenAPI plan.** Risks encoding expectations the Vue client does not
  use. Rejected in favor of the current source contract and generated springdoc documentation.
- **Test only mocks or H2.** Does not prove PostgreSQL migrations and row locks. Rejected.
- **Use Testcontainers.** Violates the explicit no-Docker constraint. Rejected.
