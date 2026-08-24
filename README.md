# Reusable Authorization Framework

A reusable Spring Boot 4.1 authorization library with a functional DB-backed core, optional admin SPA, and runnable H2 demo.

## Modules

- `authorization-core`: published DB-backed engine, Spring Security integration, JPA/Flyway, seeds, cache, audit, and functional admin REST API.
- `authorization-keycloak`: published optional-module descriptor; implementation starts in Phase 4.
- `authorization-admin-ui`: published optional React/TypeScript admin SPA packaged as Spring Boot static resources.
- `examples/authorization-demo`: non-published runnable verification application.

## Add the DB-backed core

```xml
<dependency>
  <groupId>com.example.authorization</groupId>
  <artifactId>authorization-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
authorization:
  enabled: true
  source: database
  seed:
    locations:
      - classpath:authorization/seed.yml
```

The application owns authentication and wires `DynamicRequestAuthorizationManager` into its `SecurityFilterChain`. The manager maps Spring `Authentication` to `(issuer, subject)`, selects the most-specific resource rule, and checks effective local permissions. No matching rule denies access. Provider failures are indeterminate and map to HTTP 503 through `AuthorizationServiceUnavailableHandler`.

## Seed data

YAML and Java `AuthorizationSeedContributor` inputs are combined, fully validated, and then merged transactionally. Repeated identical seeds are skipped using their checksum. Seeds reference stable logical codes rather than database IDs or SQL. URL permissions and resource rules store the HTTP method and path together as `METHOD:/path`. UI patterns are opaque identifiers matched exactly.

```yaml
authorization:
  seed:
    permissions:
      - code: EMPLOYEE_VIEW
        name: View employees
        type: URL
        pattern: GET:/employees/**
    permission-groups:
      - code: EMPLOYEE_VIEWERS
        name: Employee viewers
        permissions: [EMPLOYEE_VIEW]
    roles:
      - code: HR_VIEWER
        name: HR viewer
        permission-groups: [EMPLOYEE_VIEWERS]
    resource-rules:
      - code: EMPLOYEES
        type: URL
        pattern: "*:/employees/**"
        access-mode: AUTHORIZED
```

Java contributors use `AuthorizationSeedBuilder` to define the same concepts. Core always contributes framework administration permissions and the `AUTHZ_SYSTEM_VIEWER` and `AUTHZ_SYSTEM_ADMIN` roles, but assigns no user to them.

## Database and Flyway

Authorization migrations live at `classpath:db/authorization/migration` and use `authorization_flyway_schema_history`. This is independent of an application's `classpath:db/migration` and `flyway_schema_history`. JPA entities are top-level classes, one per file, under `persistence.entity`; table names remain explicit `AUTH_*` contracts.

## Lombok

Java-containing framework modules use Lombok as an optional, provided build-time dependency. Its version and annotation-processor path are centralized in the parent POM so compilation is deterministic on JDKs that do not discover annotation processors implicitly.

Lombok supplies constructor injection, logging, configuration/seed accessors, entity accessors, and embeddable-ID constructors/equality. Immutable domain types and DTOs remain Java records. JPA entities intentionally avoid `@Data`: generated setters are suppressed for identifiers, versions, entitlement counters, and relationship collections where mutation must remain controlled.

## Admin API

Phase 2 provides the full admin REST API: capabilities; paginated CRUD and mappings; user assignments and effective permissions; authorization explain; sync facade; and audit queries. Writes use optimistic versions, post-commit cache invalidation, and admin audit events. The API path and enablement are configured under `authorization.admin.api`.

## Optional admin UI

Add the UI alongside `authorization-core`:

```xml
<dependency>
  <groupId>com.example.authorization</groupId>
  <artifactId>authorization-admin-ui</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
authorization:
  admin:
    ui:
      enabled: true
      base-path: /authorization-admin
```

The same-origin SPA is served at `/authorization-admin/` by default and discovers both configured base paths from its protected runtime configuration endpoint. It provides capability-aware dashboard, configuration CRUD, user assignments and effective permissions, authorization explain, conditional synchronization, and audit screens.

The framework seeds the `AUTHZ_ADMIN_UI` permission into its viewer/admin permission groups, but it never assigns a user. The consuming application remains responsible for an `AUTHORIZED` resource rule covering the configured UI path and for assigning an appropriate framework admin role. Both the UI and API are protected by the normal authorization engine.

## Optional Keycloak module

The Keycloak module currently contains its Maven descriptor; implementation begins in Phase 4. Selecting `authorization.source=keycloak` without a synchronization integration fails startup with an actionable error. Runtime authorization remains local after synchronization is implemented.

## Run and verify

Requires Java 21 and Maven.

```bash
mvn clean verify
mvn -pl examples/authorization-demo -am spring-boot:run
```

The demo profile is active by default. Open the Phase 3 UI as the seeded demo manager:

```text
http://localhost:8080/authorization-admin?demo-user=manager
```

The query parameter is accepted only by the demo authentication filter, sets a one-hour HttpOnly demo cookie, and redirects to the protected UI. It is not a production authentication design.

Header authentication remains available for curl:

```bash
curl -i http://localhost:8080/demo/public
curl -i -H 'X-Demo-User: viewer' http://localhost:8080/demo/employees
curl -i -X PUT -H 'X-Demo-User: manager' http://localhost:8080/demo/employees/1
```

Never copy the demo query/cookie or `X-Demo-User` authentication mechanism into production. A consuming application should use its normal Spring Security authentication mechanism.

## Current status

Phases 1, 2, and 3 are implemented. See [TASKS.md](TASKS.md) for the remaining phased work, [docs/16-phase-1-implementation.md](docs/16-phase-1-implementation.md) for the DB-backed core, [docs/17-phase-2-implementation.md](docs/17-phase-2-implementation.md) for the admin API, and [docs/18-phase-3-implementation.md](docs/18-phase-3-implementation.md) for the admin SPA.
