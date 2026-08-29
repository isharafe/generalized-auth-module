# Caching, Audit, and Events

## ResourceRule cache

Cache active protection rules.

Characteristics:

- longer TTL is acceptable
- invalidate on resource-rule mutation
- preserve deterministic sorted/specific form

## UserEntitlement cache

Key:

```text
(issuer, subject)
```

Never raw access token.

Value should include:

```text
roles
permission groups
resolved permissions
entitlementVersion
loadedAt
```

## Entitlement version

Increment a user's authorization version when role/group assignments change.

The version is returned with entitlements and incremented only when assignments actually change.
The in-process cache is invalidated directly after commit. Multi-instance deployments can enable
the database invalidation transport or replace its provider-neutral publisher SPI.

## Invalidation

After transaction commit:

```text
invalidateIdentity(identity)
invalidateResourceRules()
```

Do not publish invalidation before commit.

## Multi-pod abstraction

Define provider-neutral invalidation event contracts. Do not hardcode Kafka/Redis.

`AuthorizationInvalidationPublisher` is the transport SPI. The default is a no-op, retaining
single-instance behavior. With `authorization.distributed-invalidation.enabled=true`, the database
implementation appends identity/resource-rule/all events and a scheduled receiver applies events
from other origins. A custom publisher can use Redis, Kafka, or another transport without changing
authorization semantics.

## Metrics

When a Micrometer registry is present, `AuthorizationObservation` records:

```text
authorization.decisions
authorization.decision.duration
authorization.cache.requests
authorization.identity.events
authorization.identity.event.duration
authorization.cache.invalidations
```

Tags are limited to decision/reason/resource type, cache/result, event result, and invalidation
scope/delivery. Stable identities, request paths, permission codes, and exception messages are not
used as tags. Without a registry, a no-op implementation keeps Micrometer optional at runtime.

## Audit events

Core exposes two explicit audit operations:

```text
publishDecision(AuthorizationResult, ProtectedResource)
publishChange(AuthorizationChangeAuditEvent)
```

Decision events describe runtime authorization outcomes. Change events describe authorization configuration or assignment mutations and can be emitted by the admin module, identity synchronization, seed processing, or future integrations. Core owns their common database persistence and correlation handling.

Every `AUTH_AUDIT_EVENT` row stores a non-null `EVENT_KIND` discriminator with `DECISION` or `CHANGE`. `EVENT_TYPE` remains the detailed subtype, such as `AUTHORIZATION_DENIED` or `ROLE_UPDATED`. The consolidated baseline migration `V1` creates this discriminator as non-null; no upgrade backfill is needed because the schema has not yet been released.

Current runtime/admin event names include:

```text
AUTHORIZATION_GRANTED
AUTHORIZATION_DENIED
AUTHORIZATION_INDETERMINATE
ADMIN_CREATE
ADMIN_UPDATE
ADMIN_DISABLE
ADMIN_MAPPING_ADD
ADMIN_MAPPING_REMOVE
IDENTITY_SYNC_STARTED
IDENTITY_SYNC_COMPLETED
IDENTITY_SYNC_FAILED
```

The Keycloak and LDAP synchronization providers emit started/completed/failed change events without
storing service-account credentials, access tokens, bind passwords, or authorization headers.

## Identity-change delivery ledger

`IdentityChangeEventProcessor` is a provider-neutral core SPI for external identity change
notifications. Its default implementation stores source, external event ID, type, stable identity,
timestamps, status, attempts, and only the failure class in `AUTH_IDENTITY_CHANGE_EVENT`. It never
stores callback request bodies, signatures, tokens, or secrets.

The ledger's source/event-ID uniqueness prevents completed deliveries from running targeted
synchronization again. Failed deliveries may be retried and stale processing claims may be reclaimed.
This event path complements, but does not replace, provider full reconciliation.

## Audit fields

Safe fields:

```text
timestamp
event kind (DECISION / CHANGE)
actor identity
target/action
request method/path
decision/reason
rule code
permission code
correlation ID
safe details JSON
```

Never store/log:

- access tokens
- refresh tokens
- client secrets
- passwords
- authorization headers
