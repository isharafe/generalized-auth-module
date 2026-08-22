# AGENTS.md — Reusable Authorization Framework

Build a reusable, functional Spring Boot authorization component. Read this file, `README.md`, `TASKS.md`, and all `docs/*.md` before coding.

## Published Maven modules

Keep the library intentionally small:

```text
authorization-parent
├── authorization-core
├── authorization-keycloak      # optional
└── authorization-admin-ui      # optional
```

Do not split Spring Security, JPA, Flyway, seed processing, caching, or admin REST into additional Maven modules. Use packages inside `authorization-core`.

A non-published runnable example may live at `examples/authorization-demo/`.

## Module responsibilities

### authorization-core

Default DB-backed implementation. It contains:

- domain model
- authorization engine
- provider/SPIs
- Spring Security `AuthorizationManager`
- JPA entities/repositories/services
- Flyway migrations and a separate Flyway history table
- default `DatabaseEntitlementProvider`
- seed YAML/Java API
- caching and invalidation
- audit hooks
- admin REST API
- Spring Boot auto-configuration

An application using only DB-backed authorization adds only this dependency.

### authorization-keycloak

Optional. It contains only Keycloak-specific integration:

- service-account authentication
- Keycloak Admin API client
- users/groups/roles retrieval
- external authority mapping
- periodic/full/targeted synchronization
- optional event-driven refresh adapter

It depends on `authorization-core`. `authorization-core` must never depend on Keycloak classes.

### authorization-admin-ui

Optional React/TypeScript admin SPA packaged as JAR static resources. It calls admin REST APIs from `authorization-core`. It contains no persistence or Keycloak logic.

## Core model

- Secured Resource / Resource Rule = LOCK
- Permission = KEY
- Permission Group = collection of keys
- Role = collection of permission groups
- User = receives roles and/or permission groups

```text
User
├── Roles
│   └── Permission Groups
│       └── Permissions
└── Permission Groups
    └── Permissions
```

Example:

```text
ResourceRule: URL | * | /api/** | AUTHORIZED
Permission:   EMPLOYEE_EDIT | URL | PUT:/api/employees/**
Request:      URL | PUT:/api/employees/123
```

Grant only when the user's effective permission string matches the request's `METHOD:/path`.

## Identity-provider neutrality

Default:

```yaml
authorization:
  source: database
```

Optional:

```yaml
authorization:
  source: keycloak
```

`source=keycloak` means Keycloak is the external identity/authority source. Application authorization semantics remain local. Keycloak groups/roles are explicitly mapped to application Roles/PermissionGroups and synchronized locally. Normal requests should still authorize from local cache/DB, not call Keycloak on every request.

## Separate concerns

Keep these independent:

1. **Authentication** — who is the caller? Normally Spring Security owns this.
2. **Entitlement retrieval** — what application roles/groups/permissions does the caller have? Default DB provider.
3. **Identity synchronization** — how external identities/authorities become local users/assignments? Keycloak module implements this when enabled.

## Application-owned authorization

The application owns:

- Roles
- PermissionGroups
- Permissions
- ResourceRules
- Role -> PermissionGroup
- PermissionGroup -> Permission

External IAM may influence only local user assignments through mappings such as:

```text
KEYCLOAK_GROUP /AD/Finance-Managers -> ROLE FINANCE_MANAGER
KEYCLOAK_ROLE payroll-approver       -> ROLE PAYROLL_APPROVER
```

Do not let external groups directly define URL permissions.

## Access modes

```text
PERMIT_ALL
AUTHENTICATED
AUTHORIZED
DENY_ALL
```

Default fail-closed behavior:

```text
No matching ResourceRule -> DENY
```

## Decisions and HTTP semantics

Internal decisions:

```text
GRANTED
DENIED
INDETERMINATE
```

Default HTTP mapping:

- protected + unauthenticated -> 401
- authenticated + denied -> 403
- required authorization infrastructure unavailable -> 503

Do not treat infrastructure failure as "no permissions".

## Spring Security

Use Spring Security's authorization pipeline with:

```java
AuthorizationManager<RequestAuthorizationContext>
```

Do not use Spring MVC `HandlerInterceptor` as the primary security mechanism.

Use Spring path matching infrastructure (`PathPattern`/equivalent). Do not implement a home-grown wildcard parser. Ignore query strings for URL authorization.

## Stable identity

Preferred identity key:

```text
(issuer, subject)
```

Username/email are mutable attributes, not stable primary identifiers.

For local demo/DB-only identities a controlled issuer such as `local` may be used.

## Assignment source

User-role and user-permission-group mappings must record:

```text
SEED
IDENTITY_SYNC
MANUAL
```

Identity synchronization may only remove/replace `IDENTITY_SYNC` assignments. Never delete `MANUAL` or `SEED` assignments during external sync.

## Flyway

Main application:

```text
classpath:db/migration
flyway_schema_history
```

Authorization component:

```text
classpath:db/authorization/migration
authorization_flyway_schema_history
```

Use a separate Flyway instance. Version numbers may overlap.

## Seed configuration

Consumers must not write SQL against authorization tables.

Support:

- YAML seed files
- Java `AuthorizationSeedContributor`

Default mode: idempotent `MERGE`.

Validate complete seed configuration before database mutation. Invalid security configuration should fail startup by default.

## Admin API and UI

Admin REST API lives in `authorization-core`. Optional UI lives in `authorization-admin-ui`.

Admin write flow:

```text
validate -> service -> transaction -> version/update -> commit -> cache invalidation -> audit
```

Controllers must not directly manipulate repositories.

Protect admin APIs/UI using the framework itself. Seed framework admin permissions/roles, but never hardcode which user receives them.

## Caching

At minimum:

- ResourceRule cache
- UserEntitlement cache

Cache key users by `(issuer, subject)`, not raw JWT.

Support targeted invalidation and entitlement versioning.

## Functional-code requirement

Generate real working code, not architecture-only skeletons.

Completed phases must not contain production TODO methods, placeholder migrations, fake repositories, or `UnsupportedOperationException` where functionality is claimed.

Every phase must compile and have tests.

## Runnable demo

Create a non-published sample app:

```text
examples/authorization-demo/
```

Default profile: H2 + database source + demo-only authentication.

It must demonstrate public, authenticated-only, authorized-view, authorized-edit, admin API, and later optional UI/Keycloak profiles.

## Technical baseline

For a new repository:

- Java 21
- Maven
- Spring Boot 4.1.x baseline, version centralized in parent POM
- Spring Security managed by Boot
- Spring Data JPA
- Flyway
- JUnit 5

If an existing repository already defines versions, respect it.

Use Lombok wherever it safely removes boilerplate. Prefer records for immutable domain types and
DTOs; use Lombok for constructor injection, logging, mutable configuration models, JPA accessors,
protected JPA constructors, and embeddable-ID equality. Keep Lombok as a compile-time-only
dependency and configure its annotation processor explicitly. Do not use broad annotations such as
`@Data` on JPA entities when they would generate unsafe setters, equality, hash-code, or
`toString` methods across identifiers or lazy associations.

Default replaceable package root:

```text
com.example.authorization
```

## Codex working method

Follow `TASKS.md` phase-by-phase. For each phase:

1. read relevant docs
2. implement a coherent slice
3. add tests
4. run Maven tests
5. fix failures
6. report assumptions/deviations
7. stop when the requested phase is complete

Ask before making an architectural or schema change not covered by this specification.
