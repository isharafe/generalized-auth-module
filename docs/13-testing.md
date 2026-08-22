# Testing Strategy

The generated project must include meaningful automated tests.

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
- framework admin seed exists
- pending user assignment resolves
- concurrent startup does not duplicate data

## Admin API

Test CRUD, all mapping families, validation, permissions, 409, audit, authorization explain, sync capabilities, and post-commit cache invalidation. The demo `AdminApiIntegrationTest` provides this Phase 2 coverage.

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

## UI

At minimum:

- frontend build succeeds
- critical component/form tests
- capability handling
- API client tests
- route/smoke test

Optional browser E2E is encouraged.

## Release-blocking security tests

Path/method matching bypass tests and admin endpoint authorization are release-blocking.
