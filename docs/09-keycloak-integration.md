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
realm-role mappings.

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
consuming application's Spring Security responsibility. Event-driven refresh is deferred to Phase 6;
periodic/full reconciliation remains the correctness path.
