# Phase 6 Implementation

Phase 6 adds optional low-latency identity refresh without moving authorization decisions or
provider-specific HTTP concerns into the wrong module.

## Provider-neutral core

`authorization-core` now defines `IdentityChangeEvent`, supported change types,
`IdentityChangeProcessingResult`, and `IdentityChangeEventProcessor`. The default database processor
dispatches a claimed event to the active `IdentitySynchronizationProvider.synchronize(identity)`.
It therefore works with any selected source that can produce a stable issuer and subject; core has
no Keycloak dependency.

Flyway migration `V2__identity_change_event.sql` adds `AUTH_IDENTITY_CHANGE_EVENT`. A unique
`(SOURCE_SYSTEM, EXTERNAL_EVENT_ID)` key and pessimistic claim transaction provide cross-instance
idempotency. Completed events return `DUPLICATE`, active claims return `IN_PROGRESS`, failures can be
redelivered, and claims older than the configurable processing timeout can be recovered. An event ID
reused for a different type or stable identity is rejected. The ledger stores no raw payload,
signature, credential, or exception message.

## Secure Keycloak adapter

`authorization-keycloak` conditionally contributes the HTTP callback only when
`authorization.keycloak.events.enabled=true`. It verifies HMAC-SHA256 over the timestamp and exact
raw body before deserialization, enforces a configurable clock-skew window, validates a minimum
32-byte secret, translates the Keycloak payload to the neutral contract, and invokes targeted sync.
The callback path and payload contract are documented in
[the Keycloak integration guide](09-keycloak-integration.md#optional-event-callback).

The receiver is intentionally not a Keycloak server plugin. A trusted Keycloak event-listener
extension or gateway must create stable event IDs and send the signed normalized payload. The
consuming application owns Spring Security and must permit and CSRF-exclude only the configured
HMAC-authenticated path.

## Correctness boundary

The callback improves propagation latency. Scheduled full Keycloak reconciliation remains available
and should remain enabled because webhooks can be lost or delayed. Runtime authorization continues
to use only the local entitlement cache and database.

## Verification

Tests cover schema migration, one-time processing, completed replay, failed-delivery retry,
event-ID conflict, active and stale claims, safe failure persistence, property validation, HMAC body
tampering, expired timestamps, payload translation, and HTTP status selection. Repository-wide
verification uses:

```bash
./mvnw clean verify
```
