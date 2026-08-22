# Reusable Authorization Framework

A reusable Spring Boot 4.1 authorization library with a functional DB-backed core and runnable H2 demo.

## Modules

- `authorization-core`: published DB-backed engine, Spring Security integration, JPA/Flyway, seeds, cache, audit, and capabilities API.
- `authorization-keycloak`: published optional-module descriptor; implementation starts in Phase 4.
- `authorization-admin-ui`: published optional-module descriptor; implementation starts in Phase 3.
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

`authorization-core` uses Lombok as an optional, provided build-time dependency. Its version and
annotation-processor path are centralized in the parent POM so compilation is deterministic on
JDKs that do not discover annotation processors implicitly.

Lombok supplies constructor injection, logging, configuration/seed accessors, entity accessors, and
embeddable-ID constructors/equality. Immutable domain types and DTOs remain Java records. JPA
entities intentionally avoid `@Data`: generated setters are suppressed for identifiers, versions,
entitlement counters, and relationship collections where mutation must remain controlled.

## Admin API

Phase 1 exposes `GET /authorization-admin/api/capabilities`. Full CRUD, mapping, audit-query, and explain endpoints are Phase 2. The API path and enablement are configured under `authorization.admin.api`.

## Optional modules

The Keycloak and admin UI modules intentionally contain Maven descriptors only in Phase 1. Selecting `authorization.source=keycloak` without a synchronization integration fails startup with an actionable error. Runtime authorization remains local even after Keycloak synchronization is implemented.

## Run and verify

Requires Java 21 and Maven.

```bash
mvn clean verify
mvn -pl examples/authorization-demo -am spring-boot:run
```

The demo profile is active by default and supports development-only authentication:

```bash
curl -i http://localhost:8080/demo/public
curl -i -H 'X-Demo-User: viewer' http://localhost:8080/demo/employees
curl -i -X PUT -H 'X-Demo-User: manager' http://localhost:8080/demo/employees/1
```

Never copy the `X-Demo-User` authentication filter into production. A consuming application should use its normal Spring Security authentication mechanism.

## Current status

Phase 1 is implemented. See [TASKS.md](TASKS.md) for the remaining phased work and [docs/16-phase-1-implementation.md](docs/16-phase-1-implementation.md) for implementation notes and limitations.
