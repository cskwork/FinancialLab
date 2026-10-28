# 0002. Require a transient password and issue short-lived RS256 access tokens

- Status: Accepted (implemented)
- Date: 2026-10-28
- Evidence: `auth/application/AuthService.java`, `auth/web/AuthController.java`,
  `auth/security/SecurityConfiguration.java`, `auth/application/LoginAttemptLimiter.java`,
  `db/demo/V2__seed_demo_user.sql`, and the frontend
  `services/api/schemas.ts`, `stores/authStore.ts`, and `pages/LoginPage.vue`.

## Context

The original frontend login request contained only an email address. It could not support server-side password
verification. The owner approved extending that login path to email and password because no deployed live
server consumed the old request. The static public demo must still open without credential friction.

## Decision

`POST /v1/auth/login` requires email and password. The login form follows existing labels, error regions, and
styles, with `autocomplete="current-password"`. Passwords remain temporary form/function values and are not
stored in Pinia, localStorage, or logs. Mock mode preserves one-click entry through a public demo default;
live mode has no password default. The mock is a demonstration, not an authentication boundary.

The backend uses `DelegatingPasswordEncoder` with bcrypt cost **12**, caps passwords at **72 UTF-8 bytes**, and
returns the same invalid-credentials response for missing users and wrong passwords. Unknown accounts perform
a real dummy hash check. The response preserves the frontend's `userId`, `email`, `role`, and `token` shape.

Account lookup uses trimmed, lowercased email, supported by V4's case-insensitive unique index. A short
repository read transaction finishes before password hashing; the CPU-bound bcrypt operation does not hold
a database connection. A semaphore permits **2** concurrent verification operations by default, waits up to configurable
**500 ms** for capacity, and returns **503** with `Retry-After: 1` if capacity remains full.

Token buckets bound login attempts per connection peer IP (**20**) and normalized email (**10**), refilling
continuously over **1 minute** by default. The process tracks at most **10000 keys** and returns **429** with
`Retry-After` on exhaustion or a full tracking map. Capacities, refill period, tracking bound, and hashing
permits are validated configuration rather than request-controlled values. Forwarded headers are disabled
by default; the limiter does not trust a submitted `X-Forwarded-For` value.

The OAuth2 resource server validates RS256 signatures, issuer, audience, expiry, and numeric subject. Access
tokens default to **15 minutes**; configured TTL must be positive and at most **1 hour**. API role checks allow
`USER` or `ADMIN`; portfolio ownership is still required for both and fails with **404** to avoid revealing a
foreign resource's existence. Sessions are stateless, CSRF is disabled for bearer calls, and CORS uses an
allowlist.

Controllers receive the validated subject through the shared `@CurrentUserId` resolver. `ADMIN` remains a
contract-compatible role; no administrative account or workflow is seeded, and it has the same resource
ownership checks as `USER`. Structured problems are parsed by the live frontend; a protected-call **401**
clears its stored session and returns to login, while wrong login credentials remain a form error.

Local/test profiles may generate an ephemeral RSA pair. Other profiles require both matching PEM resources:
PKCS#8 private and X.509 public keys, at least **2048 bits**. The resources and database secrets are external
configuration. `FINLAB_JWT_PRIVATE_KEY_LOCATION` and `FINLAB_JWT_PUBLIC_KEY_LOCATION` name PEM resources;
Spring Security's `RsaKeyConverters` parses them. No private key is checked in. The public-password demo
account migration is under `db/demo`, opted into only by `local`, `demo`, and `test`; base/production migration
locations seed no account. Selecting `demo` alone still requires configured signing keys. Do not enable
demo-bearing profiles against a production database.

## Consequences

Production password verification is explicit and the frontend contract remains typed and tested. An app restart
changes local ephemeral keys and invalidates local tokens. Production key rotation needs operational planning.
There is no refresh-token, revocation, registration, or recovery implementation. Rate buckets and semaphore
limits are per process; a multi-instance deployment needs a shared store or edge controls. A reverse proxy
can aggregate visitors into one peer-IP allowance unless its trusted-address policy is configured deliberately.
The existing frontend bearer-token storage remains; a broader production session-policy change is separate work.

## Alternatives considered

- **Email-only token issuance in live mode.** Cannot prove account ownership. Rejected.
- **Send a configured password on behalf of the frontend.** Verifies a server-held value rather than the user's
  credential. Rejected.
- **Replace the flow with cookies or an external identity provider.** Valid future options, but they materially
  change the existing bearer client and the agreed implementation scope.
