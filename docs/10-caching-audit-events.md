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

This enables stale cache detection and targeted multi-pod invalidation later.

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

Record at least:

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

## Audit fields

Safe fields:

```text
timestamp
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
