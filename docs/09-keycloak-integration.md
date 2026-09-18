# Optional Keycloak Integration

Module:

```text
authorization-keycloak
```

The module activates when it is present, `authorization.source=keycloak`, and
`authorization.keycloak.enabled=true` (the default). It contributes a Keycloak Admin API client,
a supported `IdentitySynchronizationProvider`, and optional scheduled synchronization. If
`source=keycloak` is selected without a supported provider, core fails startup with an actionable
configuration error.

## Architecture

```text
AD/LDAP
  |
Keycloak federation
  |
Keycloak Admin API
  |
authorization-keycloak synchronization
  |
local AUTH_* tables and caches
  |
authorization-core runtime authorization
```

Keycloak supplies identity and external authority facts. Application roles, permission groups,
permissions, resource rules, and authorization decisions remain local.

## Service-account client

The default client obtains an access token through the realm client-credentials endpoint and caches
it until shortly before expiry. It retrieves:

- users with configurable page size
- one user by Keycloak user ID
- each user's group memberships
- each user's realm-role mappings

Client-role mappings are not synchronized in Phase 4. Map realm roles or groups when client-role
semantics are required.

Connect/read timeouts, maximum attempts, and retry backoff are configurable. Transport problems and
HTTP failures are exposed as typed `KeycloakClientException` failures. HTTP 401 clears the cached
token; 401, 429, and 5xx responses are retryable up to the configured limit. Client secrets, tokens,
and authorization headers are never written to audit details.

Use a dedicated confidential client such as `authorization-sync-service` and grant its service
account only the realm-management permissions needed to query users, group memberships, and
realm-role mappings. Keycloak issues the effective service-account roles as the intersection of the
roles assigned to the service account and the roles allowed by the client's scope. When
`fullScopeAllowed` is false, explicitly add every required `realm-management` role to the client's
dedicated scope; assigning the role only to the service-account user produces a token without that
role and Admin API requests fail with HTTP 403.

## Explicit authority mappings

All four mapping combinations are supported:

```text
KEYCLOAK GROUP -> ROLE
KEYCLOAK GROUP -> PERMISSION_GROUP
KEYCLOAK ROLE  -> ROLE
KEYCLOAK ROLE  -> PERMISSION_GROUP
```

Mappings can be managed through the admin API/UI or seeded in YAML:

```yaml
authorization:
  seed:
    external-authority-mappings:
      - source-system: KEYCLOAK
        authority-type: GROUP
        authority: /AD/Finance-Managers
        target:
          type: ROLE
          code: FINANCE_MANAGER

      - source-system: KEYCLOAK
        authority-type: ROLE
        authority: payroll-approver
        target:
          type: PERMISSION_GROUP
          code: PAYROLL_APPROVERS
```

Java contributors use `AuthorizationSeedBuilder.externalAuthorityMapping(...)`. Seed validation
rejects missing or unknown local targets before mutation, and MERGE uses the complete
source/authority/target natural key.

External authorities never contain URL or UI permissions. They map only to application-owned roles
or permission groups.

## Stable local identity

The configured issuer and Keycloak user ID become the local stable key:

```text
(authorization.keycloak.issuer, Keycloak user id)
```

If `issuer` is omitted it resolves to `{normalized-base-url}/realms/{realm}`. Username, email,
first name, and last name are synchronized as mutable attributes. The Keycloak user ID is also stored
as the external directory ID.

## Synchronization behavior

Full reconciliation:

1. acquire a pessimistic write lock on `AUTH_SYNC_STATE.GLOBAL_IDENTITY_SYNC`
2. page through all Keycloak users
3. retrieve each user's groups and realm roles
4. map external authorities to local targets
5. upsert the local user
6. add missing `IDENTITY_SYNC` role/group assignments
7. remove stale `IDENTITY_SYNC` assignments
8. preserve all `MANUAL` and `SEED` assignments
9. resolve pending seed assignments
10. mark unseen Keycloak identities missing, disable them, and remove only sync-owned assignments
11. increment entitlement versions when authorization state changes
12. commit and invalidate affected identity caches
13. record synchronization status and audit events

The database row lock is held for the reconciliation transaction, so application instances sharing
the same database serialize synchronization work.

Targeted synchronization retrieves one user by Keycloak ID. If the user no longer exists, it applies
the same missing-user handling to that local identity.

The Keycloak Admin users API used here has no reliable modified-since contract. Therefore the Phase 4
incremental entry point deliberately performs a full reconciliation and reports
`INCREMENTAL_FULL_SCAN`. This preserves correctness while keeping a provider-neutral incremental
SPI for a future optimized source.

## Scheduling and manual execution

`full-cron` and `incremental-cron` use Spring cron syntax. Their default value, `-`, disables
that schedule. `sync.enabled=false` disables scheduler creation but leaves the provider available
for admin-triggered full, incremental, and targeted synchronization.

Status is stored in `AUTH_SYNC_STATE` as `NEVER`, `RUNNING`, `COMPLETED`, or `FAILED` with
safe details. Synchronization publishes `IDENTITY_SYNC_STARTED`, `IDENTITY_SYNC_COMPLETED`, and
`IDENTITY_SYNC_FAILED` change-audit events.

## Runtime request flow

Even with `source=keycloak`:

```text
request
 -> Spring Security authentication (often Keycloak JWT)
 -> AuthenticatedIdentity
 -> local entitlement cache
 -> local database on cache miss
 -> AuthorizationEngine
```

Normal authorization requests never call the Keycloak Admin API. Authentication remains the
consuming application's Spring Security responsibility.

## Optional event callback

Phase 6 adds an opt-in callback that translates Keycloak user changes into the provider-neutral
core `IdentityChangeEvent` contract and runs targeted synchronization. Enable it with:

```yaml
authorization:
  identity-events:
    processing-timeout: 5m

  keycloak:
    events:
      enabled: true
      path: /authorization/keycloak/events
      secret: ${AUTHORIZATION_KEYCLOAK_EVENT_SECRET}
      max-clock-skew: 5m
```

The secret must contain at least 32 UTF-8 bytes. Send an exact JSON request body such as:

```json
{
  "eventId": "01J6H6QM7Z8CVN4NPDH2Q2EJXW",
  "type": "GROUP_MEMBERSHIP_CHANGED",
  "userId": "e527961f-350e-4808-932c-56efbc5509c7",
  "occurredAt": "2026-08-29T12:00:00Z"
}
```

Supported types are `USER_CREATED`, `USER_UPDATED`, `USER_DELETED`,
`GROUP_MEMBERSHIP_CHANGED`, and `ROLE_MAPPING_CHANGED`. `occurredAt` is optional; when supplied it
must be an ISO-8601 instant. The configured issuer and `userId` form the targeted stable identity.

The sender sets:

```text
X-Authorization-Timestamp: <Unix epoch seconds>
X-Authorization-Signature: sha256=<lowercase HMAC-SHA256 hex>
```

The signed bytes are `ASCII(timestamp) + "." + exactRawRequestBody`, using the configured secret.
The receiver compares signatures in constant time and rejects timestamps outside `max-clock-skew`.
Use HTTPS in production and keep this secret separate from the Keycloak Admin API client secret.

Keycloak does not send this normalized webhook by itself. Deploy a Keycloak event-listener extension
or a trusted event gateway that assigns a stable event ID, creates this payload, and signs it. The
callback is HMAC-authenticated rather than session-authenticated, so the consuming application's
`SecurityFilterChain` must permit the configured path and exclude that exact path from CSRF checks.
Do not permit a broader path. HMAC validation still runs inside the controller before JSON parsing.

Responses are `200` for processed or already-processed deliveries, `202` when another instance has
an active claim, `400` for an invalid payload, `401` for invalid authentication, `409` when an event
ID is reused with different identity data, and `503` when targeted synchronization fails.

Core persists only event metadata and a safe failure class in `AUTH_IDENTITY_CHANGE_EVENT`. The
unique source/event ID key handles replay across pods. Failed events retry on redelivery; a
`PROCESSING` claim older than `authorization.identity-events.processing-timeout` can be reclaimed.
Successful replays never call Keycloak twice.

Event delivery can be lost, delayed, or misconfigured. Keep `full-cron` enabled as a periodic
reconciliation safety net. The callback reduces propagation delay; the local database and full
reconciliation remain the correctness path.
