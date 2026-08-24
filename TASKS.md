# Implementation Tasks

Implement phases sequentially. Do not jump to Keycloak/UI before the DB-backed core works.

# Phase 1 — Functional DB-only core

Create repository layout:

```text
pom.xml
authorization-core/
authorization-keycloak/
authorization-admin-ui/
examples/authorization-demo/
```

Only core + demo must be functional in Phase 1; optional modules may contain only their Maven descriptor/build placeholders.

## Parent build

- [x] Maven aggregator parent
- [x] Java 21 baseline
- [x] centralized Spring Boot version
- [x] dependency/plugin management
- [x] centralized Lombok version and explicit annotation processing
- [x] three reusable module declarations
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
- [x] framework admin permissions/roles seed
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
- [x] configurable migrations/seed/cache/admin API
- [x] custom SPI beans override defaults
- [x] clear failure if unsupported source selected

## Demo

- [x] H2
- [x] demo-only auth profile/header
- [x] seeded viewer/manager users
- [x] seeded roles/groups/permissions/rules
- [x] public/profile/view/edit endpoints
- [x] admin assignment for manager
- [x] integration tests prove expected 200/401/403 behavior

## Phase 1 exit

Run:

```text
mvn clean verify
```

All tests pass and demo can start.

---

# Phase 2 — Functional Admin REST API

Inside `authorization-core`:

- [x] capabilities
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
- [x] pagination/search
- [x] validation
- [x] optimistic locking / 409
- [x] post-commit cache invalidation
- [x] admin write audit
- [x] API security with framework permissions
- [x] integration tests

Exit: demo admin APIs are usable with curl/Postman and tests.

---

# Phase 3 — Optional Admin UI

Inside `authorization-admin-ui`:

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
- [x] source badges/read-only sync fields
- [x] component/smoke tests
- [x] demo includes UI and is usable

---

# Phase 4 — Optional Keycloak source

Inside `authorization-keycloak`:

## Auto-configuration

- [ ] activate when module present + `source=keycloak`
- [ ] validate Keycloak settings
- [ ] expose capabilities/sync provider

## Client

- [ ] service-account token via client credentials
- [ ] user retrieval/pagination
- [ ] group memberships
- [ ] role mappings
- [ ] configurable timeouts/retries
- [ ] typed failures

## Mapping

Support all four:

```text
KEYCLOAK_GROUP -> ROLE
KEYCLOAK_GROUP -> PERMISSION_GROUP
KEYCLOAK_ROLE  -> ROLE
KEYCLOAK_ROLE  -> PERMISSION_GROUP
```

## Synchronization

- [ ] full reconciliation
- [ ] targeted user sync
- [ ] incremental strategy where practical
- [ ] upsert local users
- [ ] add desired IDENTITY_SYNC mappings
- [ ] remove stale IDENTITY_SYNC mappings
- [ ] preserve MANUAL/SEED
- [ ] pending seed assignment resolution
- [ ] entitlement version increment
- [ ] post-commit invalidation
- [ ] audit/sync status
- [ ] multi-pod sync lock

## Tests

- [ ] mock Keycloak Admin API
- [ ] pagination
- [ ] group mapping
- [ ] role mapping
- [ ] stale removal
- [ ] manual/seed preservation
- [ ] timeout/error
- [ ] cache invalidation

## Demo

- [ ] optional Keycloak profile documentation/config

---

# Phase 5 — Optional event-driven refresh

- [ ] provider-neutral IdentityChangeEvent
- [ ] optional secure Keycloak event adapter/callback
- [ ] idempotency/replay handling
- [ ] targeted sync
- [ ] periodic reconciliation remains correctness safety net

---

# Phase 6 — Production hardening

- [ ] metrics/observability
- [ ] path bypass regression suite
- [ ] migration upgrade tests
- [ ] seed upgrade tests
- [ ] concurrency tests
- [ ] multi-pod initialization lock tests
- [ ] distributed invalidation SPI/implementation option
- [ ] performance/load tests
- [ ] admin API security review
- [ ] docs/examples polish
