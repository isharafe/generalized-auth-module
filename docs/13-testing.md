# Testing Strategy

The project includes meaningful automated tests, while later-phase and hardening coverage remains
explicitly planned below. Lists in the strategy sections are release targets for the phase that owns
the feature; they should not be read as claims that every listed case already has a dedicated test.

## Authorization engine

Test:

- no rule -> deny
- `PERMIT_ALL`
- `AUTHENTICATED`
- `DENY_ALL`
- matching permission -> grant
- no matching permission -> deny
- provider unavailable -> indeterminate
- path specificity
- exact method vs wildcard
- priority tie-break
- conflicting equivalent rules -> configuration error

## Spring Security integration

Test:

- public endpoint without auth -> allowed
- authenticated-only without auth -> 401
- protected without auth -> 401
- authenticated wrong permission -> 403
- correct permission -> controller reached
- indeterminate -> 503
- query string cannot bypass rule
- `/*`/`/**` behavior
- encoded/repeated/trailing slash cases

## Persistence

Test:

- identity lookup by issuer+subject
- unique codes
- relationships
- assignment source
- optimistic locking
- effective entitlement query
- identity sync removal preserves SEED/MANUAL

## Flyway

Create test application migration `V1` and authorization migration `V1`. Assert both history tables exist and contain independent V1 entries.

## Seed

Test:

- first apply
- identical second apply
- safe descriptive update
- unknown reference fails before mutation
- conflict fails
- optional admin module contributes its framework admin seed
- pending user assignment resolves
- concurrent startup does not duplicate data
- the baseline plus follow-up migrations create the final schema, including a non-null audit event kind, canonical resource patterns, and the identity-change ledger

## Admin API

In `authorization-admin`, test CRUD, all mapping families, validation, permissions, 409, audit, authorization explain, sync capabilities, and post-commit cache invalidation. The demo `AdminApiIntegrationTest` provides end-to-end Phase 2 coverage.

## Keycloak integration

Use a mock HTTP server for Admin API tests and optionally Testcontainers Keycloak profile if practical.

Test:

- service account flow
- user pagination
- groups
- roles
- mappings
- sync add/remove
- preservation of MANUAL/SEED
- timeout/error behavior
- targeted invalidation
- HMAC callback authentication, timestamp expiry, payload translation, and response semantics
- provider-neutral replay suppression, failed-delivery retry, event-ID conflict, and stale-claim recovery

## LDAP integration

Test:

- settings and LDAP filter-value validation
- stable string and binary identity configuration
- group and configured user-attribute mappings
- full and targeted synchronization
- stale `IDENTITY_SYNC` removal and missing-user handling
- preservation of `MANUAL`/`SEED`
- pending assignment resolution
- failure status/audit and targeted cache invalidation

## UI

The Maven lifecycle runs the frontend production build and Vitest suite. Tests cover critical component/form behavior, capability-driven navigation, API client query/error handling, and route/dashboard smoke rendering.

The demo integration suite also verifies packaged-index delivery, runtime path configuration, 401/403 protection, authorized access, and the demo browser-session bootstrap. Optional browser E2E remains encouraged.

## Current implemented coverage

The current suite covers:

- authorization decisions, rule ranking, conflicts, URL wildcard/method behavior, UI matching, and strategy overrides
- the consolidated authorization schema and the demo's separate application/authorization Flyway histories
- seed validation, fail-on-error behavior, pending-assignment resolution invocation, and post-commit cache invalidation
- demo HTTP 200/401/403/503 behavior and query-string isolation
- admin API security, CRUD/mappings, validation, optimistic conflicts, assignment ownership, explain, audit, sync facade, and cache-visible updates
- packaged UI security/runtime configuration and frontend API, dashboard, capability, permission-preview, and audit-kind behavior
- Keycloak settings validation, client-credentials authentication, pagination, groups, realm roles, typed retry/timeout failures, full and targeted synchronization, stale assignment removal, MANUAL/SEED preservation, pending assignment resolution, audit/status, and cache invalidation
- Keycloak event callback property validation, HMAC/timestamp rejection, neutral event translation, and provider-neutral durable idempotency/retry processing
- LDAP settings and filter escaping, group/attribute mapping, full and targeted synchronization,
  missing/stale removal, MANUAL/SEED preservation, pending assignment resolution, failure status,
  audit, and cache invalidation
- demo OIDC browser redirection, login-triggered targeted synchronization, `UI:seePage1`/`UI:seePage2` link visibility, and direct-page denial

Broader seed merge/idempotency upgrade tests, concurrent-startup tests, broader URL bypass regression
coverage, and real multi-process database-lock verification remain Phase 7 hardening work as
identified in `TASKS.md`.

## Release-blocking security tests

Path/method matching bypass tests and admin endpoint authorization are release-blocking.
