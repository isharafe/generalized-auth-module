# Reusable Authorization Framework

A reusable Spring Boot 4.1 authorization library with a functional DB-backed core, optional administration module, and runnable H2 demo.

## Modules

- `authorization-core`: published DB-backed engine, Spring Security integration, JPA/Flyway, seeds, cache, and audit.
- `authorization-keycloak`: published optional Keycloak Admin API client and local identity/authority synchronization.
- `authorization-admin`: published optional management REST API, services, framework-admin seeds, and React/TypeScript SPA.
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

YAML and Java `AuthorizationSeedContributor` inputs are combined, fully validated, and then merged transactionally. Repeated identical seeds are skipped using their checksum. Seeds reference stable logical codes rather than database IDs or SQL. For a supplied permission group or role, its membership list replaces the stored membership; omitting a top-level object does not delete that object. URL permissions and resource rules store the HTTP method and path together as `METHOD:/path`. UI patterns are opaque identifiers matched exactly.

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

Java contributors use `AuthorizationSeedBuilder` to define the same concepts. When present, `authorization-admin` contributes its management permissions and the `AUTHZ_SYSTEM_VIEWER` and `AUTHZ_SYSTEM_ADMIN` roles, but assigns no user to them.

## Database and Flyway

Authorization migrations live at `classpath:db/authorization/migration` and use `authorization_flyway_schema_history`. This is independent of an application's `classpath:db/migration` and `flyway_schema_history`. JPA entities are top-level classes, one per file, under `persistence.entity`; table names remain explicit `AUTH_*` contracts.

## Lombok

Java-containing framework modules use Lombok as an optional, provided build-time dependency. Its version and annotation-processor path are centralized in the parent POM so compilation is deterministic on JDKs that do not discover annotation processors implicitly.

Lombok supplies constructor injection, logging, configuration/seed accessors, entity accessors, and embeddable-ID constructors/equality. Immutable domain types and DTOs remain Java records. JPA entities intentionally avoid `@Data`: generated setters are suppressed for identifiers, versions, entitlement counters, and relationship collections where mutation must remain controlled.

## Optional administration module

Add `authorization-admin` when the application needs management APIs or the built-in UI. It depends on `authorization-core`, so adding this dependency provides both runtime authorization and administration:

```xml
<dependency>
  <groupId>com.example.authorization</groupId>
  <artifactId>authorization-admin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
authorization:
  admin:
    api:
      enabled: true
      base-path: /authorization-admin/api
    ui:
      enabled: true
      base-path: /authorization-admin
```

The module owns the management REST controllers, DTOs, transactional services, framework-admin seed definitions, and same-origin SPA. The API provides capabilities, paginated CRUD and mappings, user assignments and effective permissions, authorization explain, synchronization facade, and audit queries. Writes use optimistic versions, post-commit cache invalidation, and generic authorization-change audit events persisted by core. Every audit row has an explicit `DECISION` or `CHANGE` kind that is returned and filterable through the admin API and UI.

The SPA is served at `/authorization-admin/` by default and discovers both configured base paths from its protected runtime configuration endpoint. The API may run without the UI, but the UI requires the API to be enabled. The module seeds `AUTHZ_ADMIN_UI` and its API permissions into framework viewer/admin groups, but never assigns a user. The consuming application remains responsible for applicable `AUTHORIZED` resource rules and an appropriate admin assignment.

An application that includes only `authorization-core` gets the authorization runtime and persistence, but no management endpoints, framework-admin seed definitions, or SPA.

## Optional Keycloak module

Add `authorization-keycloak` when Keycloak supplies external identities and authorities. It depends on
`authorization-core` and activates only when `authorization.source=keycloak`:

```xml
<dependency>
  <groupId>com.example.authorization</groupId>
  <artifactId>authorization-keycloak</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Configure a confidential service-account client under `authorization.keycloak`. The module retrieves
paged users, group memberships, and realm-role mappings, then reconciles only `IDENTITY_SYNC`
assignments into the local database. `MANUAL` and `SEED` assignments are preserved. Full,
targeted, and scheduled synchronization update status/audit data and invalidate affected entitlement
cache entries after commit. Normal authorization requests still use the local cache/database and
never call Keycloak. See [the Keycloak integration guide](docs/09-keycloak-integration.md).

## Run and verify

Requires Java 21. The repository-level Maven Wrapper pins Maven 3.9.11 for every module, so a separate Maven installation is not required.

```bash
./mvnw clean verify
./mvnw -pl examples/authorization-demo -am package
java -jar examples/authorization-demo/target/authorization-demo-0.1.0-SNAPSHOT.jar
```

On Windows, use `mvnw.cmd` in place of `./mvnw`. Run the wrapper from the repository root so all modules use the same reactor and Maven version.

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

For a real browser authentication demonstration, run the `keycloak-demo` profile and open:

```text
http://localhost:8080/demo-ui/
```

The browser redirects to Keycloak using Authorization Code/OIDC login. On success, the demo performs
a targeted identity synchronization and renders links to sample pages according to
`UI:seePage1` and `UI:seePage2`. Sign out uses OIDC RP-initiated logout to end both the local
application session and the Keycloak SSO session. See
[the demo Keycloak setup](docs/14-demo-application.md#keycloak-profile).

## Current status

Phases 1 through 4 are implemented. See [TASKS.md](TASKS.md) for the remaining phased work and [docs/19-phase-4-implementation.md](docs/19-phase-4-implementation.md) for the optional Keycloak synchronization implementation.
