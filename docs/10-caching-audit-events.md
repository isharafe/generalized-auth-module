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

The version is returned with entitlements and incremented when assignments change. The current
in-process cache is invalidated directly after commit; version-based stale-cache detection and
multi-pod invalidation remain future hardening work.

## Invalidation

After transaction commit:

```text
invalidateIdentity(identity)
invalidateResourceRules()
```

Do not publish invalidation before commit.

## Multi-pod abstraction

Define provider-neutral invalidation event contracts. Do not hardcode Kafka/Redis.

Initial implementation may use local cache plus documented limitations, then add distributed adapters later.

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
