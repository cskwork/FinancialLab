# Verification: 2026-10-28, Round 3 final polish

All 13 Round 3 items and the previous local acceptance gates pass. Changes remain uncommitted on
`feat/spring-backend`; HEAD remains `main` at `423deee`. No commit, push, merge or deployment occurred.
Other contributors' uncommitted documentation edits were preserved; only the requested corrections and
current test counts were added. Existing QA processes on ports 8080 and 5173 were left running.

## Executed checks

```sh
cd backend
JAVA_HOME=<Temurin 21.0.13> MAVEN_USER_HOME=<local repo> \
  ./mvnw -B -Dmaven.repo.local=<local repo> clean verify
```

**exit 0, BUILD SUCCESS; 225 passed, 0 failures, 0 errors, 0 skipped**.
Finished **2026-10-28 12:37:27 +09:00**, elapsed **36.028 seconds**.
Local log: `<scratch>/finlab-round3-verify.log`; **34** Surefire XML files under `target/surefire-reports/`.

| Layer | Passed | Failures / errors / skipped |
| --- | ---: | --- |
| domain | 47 | 0 / 0 / 0 |
| application | 101 | 0 / 0 / 0 |
| web | 26 | 0 / 0 / 0 |
| integration | 21 | 0 / 0 / 0 |
| common/security | 13 | 0 / 0 / 0 |
| architecture | 8 | 0 / 0 / 0 |
| persistence | 6 | 0 / 0 / 0 |
| config | 3 | 0 / 0 / 0 |

The web count includes 24 controller tests and 2 response-mapping tests. Integration includes 18 original
API scenarios plus 3 new listing scenarios in `TransactionListingIntegrationTest`, physically located under
`transaction/persistence`. Tests use native Zonky PostgreSQL **14.22**. Docker/Testcontainers executions,
Docker-dependent exclusions and skipped tests are all **0**.

### JaCoCo

| Selected packages | Covered / total lines | Line coverage |
| --- | ---: | ---: |
| Application | 273 / 275 | 99.27% |
| Domain | 212 / 212 | 100.00% |
| Combined | 485 / 487 | 99.59% |

The gate requires at least **80% in every application/domain package** and passed. The lowest selected
package is `transaction.application`, **74/76 = 97.37%**; every other selected package is 100%.
The two uncovered application lines handle JVM SHA-256 unavailability. Overall: **790/799 lines = 98.87%**,
**301/335 branches = 89.85%**. Report: `target/site/jacoco/`.

The fallback without JaCoCo was also rerun:
`./mvnw -B -Dmaven.repo.local=<local repo> -Djacoco.skip=true -Dtest=FinLabPropertiesTest test`
→ **exit 0, BUILD SUCCESS, 1 passed**. The full-suite counts above were captured before this targeted check.

### Frontend and real-response contract

From `frontend/`, on 2026-10-28:

- `npm test -- --run` → **exit 0, 183 passed, 17 files**, 0 failed/skipped.
  Log: `<scratch>/finlab-round3-frontend-tests.log`.
- `npm run build` → **exit 0**, TypeScript and Vite passed.
  Log: `<scratch>/finlab-round3-frontend-build.log`. Existing Zod annotation, shared dynamic-import and
  >500 kB chunk warnings remain; no warning was suppressed.
- `FINLAB_CONTRACT_ACTUAL_DIR=backend/target/contract-actual npm test -- --run src/services/api/contract.spec.ts`
  → **exit 0, 38 passed**: 18 committed fixtures + 18 real response examples + 2 completeness checks.
  Log: `<scratch>/finlab-round3-actual-contract.log`.

The full integration run regenerated all 18 files under `backend/target/contract-actual/`. Both committed
and actual problem bodies must have `urn:finlab:problem:<kebab-case-code>` matching their code. Existing
UTC-Z instant, email, enum and finite-number assertions remain enabled. The login artifact intentionally
contains a short-lived JWT signed by the ephemeral test key; it contains no production credential.
Remote GitHub Actions was not run because pushing is prohibited; its actual-response Zod step remains configured.

## Round 3 item evidence

| Item | Result and regression evidence |
| --- | --- |
| 1 | SQL filters run before newest-N selection, `timestamp DESC, id DESC`; raw array contract preserved, `X-Total-Count` exposed by CORS. `TransactionListingIntegrationTest` covers 1,004 rows, 1,000-row limit, filters/count, equal timestamps and empty results. |
| 2 | Integrity failures return 409 `DATA_INTEGRITY`, logged at WARN with the cause; lock failures retain `CONCURRENT_MODIFICATION`. `ApiExceptionHandlerTest` asserts status/code/URN, safe body and log throwable; ko/en localization covers the new reason. |
| 3 | Semaphore uses timed `tryAcquire`: `finlab.login.hashing-wait` / `FINLAB_LOGIN_HASHING_WAIT`, default 500ms, positive and at most 5s. `AuthCapacityTest` covers timeout, capacity released during wait, interruption and permit release. |
| 4 | `assertShape` comment accurately identifies the ephemeral test JWT in the login artifact. |
| 5 | Five committed problem fixtures now use the actual FinLab URNs. Backend and Zod assertions enforce type/code correspondence for actual responses and examples. |
| 6 | Default URL and all frontend placeholders use `http://localhost:8080`; existing user-saved URLs remain intact. `configStore.spec.ts` covers both default and persistence. Related docs/PRD examples agree. |
| 7 | Each live transaction submit gets a UUID; unchanged failed submit retries reuse it, edited/new successful submits rotate it; mock sends no key. `apiClient.spec.ts` and `pageBehavior.spec.ts` cover headers and page→store→API submit lifecycle. |
| 8 | Readable `details.fieldErrors` replace the generic message with specific reasons, preserving details; end-date ordering is localized in ko/en. API and localized-message tests cover valid, empty and malformed field details. |
| 9 | Expiry preserves fullPath, including query/hash, in `/login?redirect=...`; GoalsPlansPage rechecks auth after portfolio loading before navigating. API and page regression tests cover both the 401 handling and dashboard-overwrite race. |
| 10 | Positive monetary fields use min/step 0.0001; current amount permits zero. `pageBehavior.spec.ts` checks defaults 10000/500, attributes, exact decimal step alignment and minimum boundary. |
| 11 | Holdings hint and average-cost term state buy fees are included in both locales; locale regression assertions pin this meaning. |
| 12 | BUY total adds fees; SELL total deducts fees in form and list. Component tests verify 2×100 with fee 3 displays $203.00 / $197.00. |
| 13 | Frontend README links current backend/root docs, removes stale claims/env/links; PRD placeholder points to real backend docs; deploy workflow loses only the commented backend-samples block. All 10 edited-doc relative links resolve; workflow diff is restricted to that block. |

Previous acceptance behaviors remain covered: transaction serialization and full FIFO validation, concurrent
oversells, concurrent same-key replay, BUY-delete versus SELL race, scoped ownership, password login without a
hashing-time JDBC transaction, demo-only migrations, lock timeout, expiry purge, email uniqueness, plan date
validation, fixed-scale output, ArchUnit cycles/layers, structured errors and Swagger assets/CSP.
Internal ledger validation and valuation still read the complete history independently of the HTTP limit.

## Live smoke and cleanup

A fresh disposable Homebrew PostgreSQL **16.14** cluster under `<scratch>/pgdata/finlab-round3.*` ran on
**127.0.0.1:60372** with database `finlab_round3_20261028`, user `finlab`. The final JAR ran with `local` at
**127.0.0.1:60373**. All migrations, including profile-scoped demo data, ran against this fresh database.

```sh
cd backend
FINLAB_URL=http://127.0.0.1:60373 bash scripts/smoke.sh
```

**exit 0**. Login, portfolio CRUD, BUY/SELL, rejected oversell, exact idempotent replay, changed-body rejection,
create/read decimal scales, holdings and metrics passed. Listing additionally verifies newest-first order,
`X-Total-Count: 2` and its CORS exposure. Swagger HTML and every referenced JS/CSS asset returned success;
Swagger-only inline-style allowance and strict API CSP remain valid. This run supplies HTTP/asset/CSP evidence;
it does not claim a new browser QA run.

[Smoke transcript](smoke-2026-10-28.txt) preserves response decimals; login token is redacted and the password
is absent. JAR SHA-256: `85cf82c60f39432ecc5d3ca8c64937ad9d038258dd1c6167b8af70bbfc8a1803`.
The app logged graceful shutdown; PostgreSQL reported `server stopped`. Both owned ports are confirmed closed,
while the original QA listeners remain at 8080 (PID 88624) and 5173 (PID 87745). Cluster data is retained outside
the repo. No `rm -rf` was used. Local evidence: `<scratch>/finlab-round3-app.log`,
`<scratch>/finlab-round3-postgres-stop.log`, `<scratch>/finlab-round3-summary.json`.

## Boundaries

No requested item is unfinished. Login limits remain per process; multi-instance operation needs a shared
limiter. Transaction arrays return the newest configured limit and expose the full matching count, with no
continuation cursor. Prices are bundled samples. Token refresh/revocation and registration/recovery remain
outside scope. No remote backend deployment or remote CI run is claimed.

Versions unchanged: Java **21.0.13+9**, Spring Boot **4.1.2**, Maven **3.9.16** / Wrapper **3.3.4**,
springdoc **3.2.0**, Zonky **2.2.2**, ArchUnit **1.5.1**, JaCoCo **0.8.14**.
