# Implementation Tasks

Implement phases sequentially. Do not jump to Keycloak/UI before the DB-backed core works.

# Phase 1 — Functional DB-only core

Create repository layout:

```text
pom.xml
authorization-core/
authorization-keycloak/
authorization-ldap/
authorization-admin/
examples/authorization-demo/
```

Only core + demo must be functional in Phase 1; optional modules may contain only their Maven descriptor/build placeholders.

## Parent build

- [x] Maven aggregator parent
- [x] Java 21 baseline
- [x] centralized Spring Boot version
- [x] dependency/plugin management
- [x] centralized Lombok version and explicit annotation processing
- [x] four reusable module declarations
- [x] demo build strategy documented/integrated

## Domain + SPI

- [x] AccessMode
- [x] AuthorizationDecision
- [x] AuthorizationReason
- [x] AuthenticatedIdentity
- [x] ResourceRule
- [x] Permission
- [x] UserEntitlements
- [x] AssignmentSource
- [x] EntitlementProvider
- [x] ResourceRuleProvider
- [x] PermissionMatcher
- [x] IdentitySynchronizationProvider
- [x] ExternalAuthorityMapper
- [x] AuthorizationCacheInvalidator
- [x] AuthorizationAuditPublisher

## Authorization engine

- [x] default deny
- [x] rule specificity
- [x] access-mode logic
- [x] permission evaluation
- [x] explainable result
- [x] indeterminate handling
- [x] unit tests

## Spring Security

- [x] URL ProtectedResource
- [x] Spring Authentication -> AuthenticatedIdentity resolver
- [x] Spring PathPattern permission/rule matching
- [x] AuthorizationManager<RequestAuthorizationContext>
- [x] 401/403/503 behavior
- [x] integration tests

## Persistence

- [x] all baseline AUTH_* tables/entities
- [x] repositories
- [x] database entitlement provider
- [x] database resource-rule provider
- [x] effective permission loading
- [x] assignment source rules
- [x] optimistic locking

## Flyway

- [x] `classpath:db/authorization/migration`
- [x] separate `authorization_flyway_schema_history`
- [x] authorization Flyway bean/lifecycle
- [x] startup ordering
- [x] separate-history integration test

## Seed

- [x] YAML seed loader
- [x] Java AuthorizationSeedContributor
- [x] merged definition
- [x] validation before persistence
- [x] idempotent MERGE
- [x] Java seed contributor SPI for optional modules
- [x] seed history/checksum
- [x] pending user assignments

## Cache + audit

- [x] resource-rule cache
- [x] entitlement cache keyed by issuer+subject
- [x] entitlement version
- [x] targeted invalidation
- [x] safe audit persistence/publisher

## Auto-configuration

- [x] `authorization.enabled`
- [x] `authorization.source=database` default
- [x] DB provider defaults
- [x] configurable migrations/seed/cache
- [x] custom SPI beans override defaults
- [x] clear failure if unsupported source selected

## Demo

- [x] H2
- [x] demo-only auth profile/header
- [x] seeded viewer/manager users
- [x] seeded roles/groups/permissions/rules
- [x] public/profile/view/edit endpoints
- [x] application authorization assignments for demo users
- [x] integration tests prove expected 200/401/403 behavior

## Phase 1 exit

Run:

```text
./mvnw clean verify
```

All tests pass and demo can start.

---

# Phase 2 — Functional Admin REST API

Inside `authorization-admin`:

- [x] capabilities
- [x] current authenticated-user details
- [x] user query/assignments
- [x] role CRUD
- [x] permission-group CRUD
- [x] permission CRUD
- [x] resource-rule CRUD
- [x] role/group mappings
- [x] group/permission mappings
- [x] user/role mappings
- [x] user/group mappings
- [x] external authority mapping CRUD
- [x] effective permissions
- [x] authorization explain/test
- [x] sync status/action facade
- [x] audit query
- [x] versioned full data export and replacement import
- [x] pagination/search
- [x] validation
- [x] optimistic locking / 409
- [x] post-commit cache invalidation
- [x] admin write audit
- [x] framework admin permissions/roles seed
- [x] API security with framework permissions
- [x] integration tests

Exit: demo admin APIs are usable with curl/Postman and tests.

---

# Phase 3 — Optional Admin UI

Inside `authorization-admin`:

- [x] React + TypeScript project
- [x] Maven-integrated frontend build
- [x] package static assets in JAR
- [x] configurable base path
- [x] API client
- [x] capabilities handling
- [x] dashboard
- [x] users
- [x] roles
- [x] permission groups
- [x] permissions
- [x] resource rules
- [x] external mappings
- [x] effective permissions
- [x] authorization test/explain
- [x] sync screen when supported
- [x] audit
- [x] current authenticated-user summary
- [x] destructive full-replacement data transfer page
- [x] source badges/read-only sync fields
- [x] component/smoke tests
- [x] demo includes UI and is usable

---

# Phase 4 — Optional Keycloak source

Inside `authorization-keycloak`:

## Auto-configuration

- [x] activate when module present + `source=keycloak`
- [x] validate Keycloak settings
- [x] expose capabilities/sync provider

## Client

- [x] service-account token via client credentials
- [x] user retrieval/pagination
- [x] group memberships
- [x] role mappings
- [x] configurable timeouts/retries
- [x] typed failures

## Mapping

Support all four:

```text
KEYCLOAK_GROUP -> ROLE
KEYCLOAK_GROUP -> PERMISSION_GROUP
KEYCLOAK_ROLE  -> ROLE
KEYCLOAK_ROLE  -> PERMISSION_GROUP
```

## Synchronization

- [x] full reconciliation
- [x] targeted user sync
- [x] incremental strategy where practical
- [x] upsert local users
- [x] add desired IDENTITY_SYNC mappings
- [x] remove stale IDENTITY_SYNC mappings
- [x] preserve MANUAL/SEED
- [x] pending seed assignment resolution
- [x] entitlement version increment
- [x] post-commit invalidation
- [x] audit/sync status
- [x] multi-pod sync lock

## Tests

- [x] mock Keycloak Admin API
- [x] pagination
- [x] group mapping
- [x] role mapping
- [x] stale removal
- [x] manual/seed preservation
- [x] timeout/error
- [x] cache invalidation

## Demo

- [x] optional Keycloak profile documentation/config

---

# Phase 5 — Optional LDAP source

Inside `authorization-ldap`:

## Auto-configuration

- [x] activate when module present + `source=ldap`
- [x] validate LDAP connection/search settings
- [x] expose capabilities/sync provider

## Client and mapping

- [x] service-bind and anonymous-bind directory access
- [x] LDAPS and multiple provider URLs
- [x] user full/targeted retrieval and optional paging
- [x] `memberOf` and group-search membership retrieval
- [x] configured user attributes as explicit external authorities
- [x] `LDAP GROUP -> ROLE/PERMISSION_GROUP`
- [x] `LDAP ATTRIBUTE -> ROLE/PERMISSION_GROUP`
- [x] stable `entryUUID` and binary `objectGUID` support
- [x] filter escaping, timeouts, referrals, and typed failures

## Synchronization and tests

- [x] full, targeted, and correctness-preserving incremental full scan
- [x] local user upsert and missing-user handling
- [x] reconcile only IDENTITY_SYNC assignments
- [x] preserve MANUAL/SEED and resolve pending assignments
- [x] entitlement versioning, post-commit invalidation, audit/status, and DB lock
- [x] properties/filter tests
- [x] group/attribute mapping integration tests
- [x] stale removal, MANUAL/SEED preservation, targeted sync, and failure tests

---

# Phase 6 — Optional event-driven refresh

- [x] provider-neutral IdentityChangeEvent
- [x] optional secure Keycloak event adapter/callback
- [x] idempotency/replay handling
- [x] targeted sync
- [x] periodic reconciliation remains correctness safety net

---

# Phase 7 — Production hardening

- [x] metrics/observability
- [x] path bypass regression suite
- [x] migration upgrade tests
- [x] seed upgrade tests
- [x] concurrency tests
- [x] multi-pod initialization lock tests
- [x] distributed invalidation SPI/implementation option
- [x] performance/load tests
- [x] admin API security review
- [x] docs/examples polish
