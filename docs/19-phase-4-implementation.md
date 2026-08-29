# Phase 4 Implementation

Phase 4 delivers the optional Keycloak identity and authority source while keeping request-time
authorization local.

## Module activation and client

`authorization-keycloak` auto-configures only for `authorization.source=keycloak` when
`authorization.keycloak.enabled` is true. It validates connection, paging, timeout, and retry
settings, then contributes:

- a service-account `HttpKeycloakAdminClient`
- a supported `KeycloakIdentitySynchronizationProvider`
- optional full/incremental scheduled jobs

The client caches client-credentials tokens, pages users, retrieves targeted users, groups, and realm
roles, and reports typed authentication, transport, rate-limit, remote-server, not-found, and
invalid-response failures. Retry policy covers 401, 429, 5xx, and transport errors.

## Local mapping and synchronization

YAML and Java seeds now support external-authority mappings. Validation resolves targets against the
complete seed before mutation, and MERGE uses the mapping natural key. The synchronizer supports all
GROUP/ROLE to ROLE/PERMISSION_GROUP combinations through core's provider-neutral
`ExternalAuthorityMapper`.

Full and targeted synchronization upsert users by configured issuer plus Keycloak user ID, reconcile
only `IDENTITY_SYNC` assignments, preserve `MANUAL` and `SEED`, resolve pending assignments,
increment entitlement versions for authorization changes, and invalidate affected identity caches
after commit. Missing users are disabled and lose only sync-owned assignments.

`AUTH_SYNC_STATE.GLOBAL_IDENTITY_SYNC` is both the status record and a pessimistically locked
database row, serializing synchronization across instances that share the database. Started,
completed, and failed operations publish change-audit events with safe details.

## Deliberate boundaries

- Realm roles are synchronized; Keycloak client roles are not part of Phase 4.
- The incremental SPI currently performs a full scan and records `INCREMENTAL_FULL_SCAN`, because
  the integrated users endpoint has no reliable modified-since contract.
- Event-driven refresh and replay/idempotency handling are delivered by Phase 6; see
  [the Phase 6 implementation summary](21-phase-6-implementation.md).
- Phase 7 adds shared-database lock/concurrency verification, cross-instance cache invalidation, and
  operational metrics; see [the Phase 7 summary](22-phase-7-implementation.md).
- Authentication remains application-owned; the Keycloak demo uses Spring OAuth2/OIDC login for
  browser sessions and Resource Server JWT for bearer-token API calls.

## Demo

The non-published demo includes the optional Keycloak module, a `keycloak-demo` profile, OAuth2/OIDC
browser login, JWT resource-server security, separate browser/service-account client configuration,
and seeded group/realm-role mappings. Successful browser login triggers targeted synchronization
before redirecting to `/demo-ui/`.

The seed also demonstrates opaque UI resources. `UI:seePage1` is granted to viewers, while
`UI:seePage1` and `UI:seePage2` are granted to managers. The server-rendered landing page hides
unavailable links, and direct page navigation repeats the authorization check. The default
H2/database/demo-authentication profile remains unchanged.

## Verification

Phase 4 tests cover settings validation, client credentials, token reuse, pagination, targeted user
lookup, group and realm-role retrieval, retry/timeout failures, all mapping families, full/targeted
sync, stale removal, MANUAL/SEED preservation, pending assignment resolution, entitlement/cache
updates, failure rollback, sync status, audit events, browser login redirects, login-triggered
synchronization, UI link visibility, and direct-page denial. Repository-wide verification is performed
with:

```bash
./mvnw clean verify
```
