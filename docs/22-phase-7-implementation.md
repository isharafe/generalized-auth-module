# Phase 7 Implementation

Phase 7 hardens the completed framework for production operation without changing the module or
authorization ownership boundaries.

## Observability and cache coordination

Core exposes `AuthorizationObservation` and supplies a Micrometer implementation whenever a
`MeterRegistry` exists. It records low-cardinality decision/latency, cache hit/miss,
identity-event, invalidation, logical persistence, synchronization, post-login initialization, and
external Keycloak/LDAP request metrics. Each physical page and retry is observed separately. A
no-op implementation keeps metrics optional and the observation SPI remains replaceable.

`AuthorizationInvalidationPublisher` is the provider-neutral cross-instance transport contract.
Single-instance deployments use the no-op publisher. The opt-in database implementation persists
bounded invalidation events, polls them in batches, suppresses the publishing origin's echo, and
cleans records older than the configured retention. Runtime authorization still reads only the
local cache/database and never depends on a synchronous remote call.

Concurrent misses for the same identity or resource-rule cache population are collapsed so only one
delegate load occurs. Assignment version increments now happen only for real assignment changes.

## Initialization and upgrade safety

Migration V3 adds `AUTH_SYNC_STATE.GLOBAL_SEED_INITIALIZATION` and the database invalidation table.
Seed initialization pessimistically locks that row before applying the fully validated combined
seed. Concurrent transaction tests verify serialization, idempotent history, and a single result.
Upgrade tests migrate an existing V1 schema through all current migrations while preserving stored
authorization data. The baseline schema validates type-qualified permission codes without changing
the numeric-ID membership model. Seed-upgrade tests verify membership replacement, descriptive
changes, checksum history, and rollback before mutation for invalid input.

## Security and load regression coverage

HTTP integration tests cover context-relative paths, trailing/repeated separators, encoded slash and
backslash attempts, semicolon parameters, query isolation, and method-override headers. The request
authorization manager removes the servlet context path before matching application-owned rules.

The admin seed now grants separate exact permissions for the UI root, runtime configuration, static
assets, and current-user API. A broad UI GET permission can no longer overlap the admin API.
Unauthenticated and non-admin access is checked across every admin capability family.

Concurrency tests cover cache population and provider-neutral event claims. A sustained cached
authorization test verifies that repeated decisions remain local and complete within a generous
regression bound. Deployment-specific capacity testing remains the responsibility of the consuming
application because its database, pool, ruleset, hardware, and latency targets determine meaningful
production limits.

The demo's opt-in performance profile complements those regression tests with exact per-request
JDBC execution counts, total JDBC time, structured request logs, and repeatable cold/warm request
and targeted-Keycloak synchronization reports. Diagnostic JDBC instrumentation remains outside the
published library modules.

## Verification

The complete backend/frontend reactor is verified with:

```bash
./mvnw clean verify
```
