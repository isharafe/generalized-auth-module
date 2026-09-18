# Reusable Authorization Framework

A reusable Spring Boot 4.1 authorization library with a functional DB-backed core, optional administration module, and runnable H2 demo.

## Modules

- `authorization-core`: published DB-backed engine, Spring Security integration, JPA/Flyway, seeds, cache, and audit.
- `authorization-keycloak`: published optional Keycloak Admin API client and local identity/authority synchronization.
- `authorization-ldap`: published optional LDAP directory client and local identity/authority synchronization.
- `authorization-admin`: published optional management REST API, services, framework-admin seeds, and React/TypeScript SPA.
- `examples/authorization-demo`: non-published runnable verification application.

## Add the DB-backed core

```xml
<dependency>
  <groupId>io.github.isharafe</groupId>
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

Core always supplies `DynamicRequestAuthorizationManager`. It maps Spring `Authentication` to
`(issuer, subject)`, selects the most-specific resource rule, and checks effective local
permissions. No matching rule denies access; provider failures are indeterminate and map to HTTP
503.

For OAuth2/OIDC browser applications, enable
`authorization.security.cookie-oauth2.enabled=true` and core supplies the login and application
`SecurityFilterChain` beans, stateless bearer-cookie authentication, CSRF support, serialized
refresh support for the admin SPA, and local or provider logout. When this mode is disabled, the
application must declare its own `SecurityFilterChain`. Declaring any chain also makes the default
core chains back off, so applications retain a complete override.

## Seed data

YAML and Java `AuthorizationSeedContributor` inputs are combined, fully validated, and then merged transactionally. Repeated identical seeds are skipped using their checksum. Seeds reference stable logical codes rather than database IDs or SQL. Permission codes are type-qualified as `<RESOURCE_TYPE>:<LOCAL_CODE>`, so `URL:VIEW` and `UI:VIEW` are distinct keys. For a supplied permission group or role, its membership list replaces the stored membership; omitting a top-level object does not delete that object. URL permissions and resource rules store the HTTP method and path together as `METHOD:/path`. UI patterns are opaque identifiers matched exactly.

```yaml
authorization:
  seed:
    permissions:
      - code: URL:EMPLOYEE_VIEW
        name: View employees
        type: URL
        pattern: GET:/employees/**
    permission-groups:
      - code: EMPLOYEE_VIEWERS
        name: Employee viewers
        permissions: [URL:EMPLOYEE_VIEW]
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

Permission codes and their resource types are immutable after creation. The baseline schema enforces
that a permission code starts with its stored resource type. Consumer-owned seed files and API
clients and version-1 administration exports use canonical codes such as `URL:EMPLOYEE_VIEW`.

## Database and Flyway

Authorization migrations live at `classpath:db/authorization/migration` and use `authorization_flyway_schema_history`. This is independent of an application's `classpath:db/migration` and `flyway_schema_history`. JPA entities are top-level classes, one per file, under `persistence.entity`; table names remain explicit `AUTH_*` contracts.

## Lombok

Java-containing framework modules use Lombok as an optional, provided build-time dependency. Its version and annotation-processor path are centralized in the parent POM so compilation is deterministic on JDKs that do not discover annotation processors implicitly.

Lombok supplies constructor injection, logging, configuration/seed accessors, entity accessors, and embeddable-ID constructors/equality. Immutable domain types and DTOs remain Java records. JPA entities intentionally avoid `@Data`: generated setters are suppressed for identifiers, versions, entitlement counters, and relationship collections where mutation must remain controlled.

## Optional administration module

Add `authorization-admin` when the application needs management APIs or the built-in UI. It depends on `authorization-core`, so adding this dependency provides both runtime authorization and administration:

```xml
<dependency>
  <groupId>io.github.isharafe</groupId>
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

The SPA is served at `/authorization-admin/` by default and discovers both configured base paths
from its protected runtime configuration endpoint. The API may run without the UI, but the UI
requires the API to be enabled. The module seeds exact UI entry/config/asset permissions and
operation-specific API permissions into framework viewer/admin groups, but never assigns a user.
UI permissions deliberately do not overlap the `/authorization-admin/api/**` namespace. The
consuming application remains responsible for applicable `AUTHORIZED` resource rules and an
appropriate admin assignment.

The Data transfer page exports a versioned JSON snapshot containing all permissions, permission
groups, roles, relationships, resource rules, users, user assignments, pending assignments, and
external authority mappings. Import is deliberately replacement-only: the complete file is
validated before mutation, then all current portable authorization data is deleted and recreated
in one transaction. Invalid files leave existing data unchanged. The UI requires an explicit
destructive-operation acknowledgement and warns that importing a snapshot without the current
administrator can remove their access. Audit events, Flyway and seed history, cache contents,
synchronization runtime state, and authentication credentials are not part of the snapshot.

An application that includes only `authorization-core` gets the authorization runtime and persistence, but no management endpoints, framework-admin seed definitions, or SPA.

## Optional Keycloak module

Add `authorization-keycloak` when Keycloak supplies external identities and authorities. It depends on
`authorization-core` and activates only when `authorization.source=keycloak`:

```xml
<dependency>
  <groupId>io.github.isharafe</groupId>
  <artifactId>authorization-keycloak</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Configure a confidential service-account client under `authorization.keycloak`. The module retrieves
paged users, group memberships, and realm-role mappings, then reconciles only `IDENTITY_SYNC`
assignments into the local database. `MANUAL` and `SEED` assignments are preserved. Full,
targeted, and scheduled synchronization update status/audit data and invalidate affected entitlement
cache entries after commit. Normal authorization requests still use the local cache/database and
never call Keycloak.

An opt-in HMAC-authenticated callback can turn Keycloak identity events into provider-neutral,
idempotent targeted synchronization. Replayed event IDs do not synchronize twice, failed deliveries
can be retried, and stale in-progress claims are recoverable. Keep a scheduled full reconciliation
enabled because event delivery is an optimization, not the correctness boundary. See
[the Keycloak integration guide](docs/09-keycloak-integration.md).

## Optional LDAP module

Add `authorization-ldap` when a standard LDAP directory supplies external users, groups, and user
attributes. It depends on `authorization-core` and activates only when
`authorization.source=ldap`:

```xml
<dependency>
  <groupId>io.github.isharafe</groupId>
  <artifactId>authorization-ldap</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
authorization:
  source: ldap
  ldap:
    urls: [ldaps://directory.example.com:636]
    base-dn: dc=example,dc=com
    bind-dn: cn=authorization-sync,ou=services,dc=example,dc=com
    bind-password: ${AUTHORIZATION_LDAP_BIND_PASSWORD}
    issuer: company-ldap

    user:
      base-dn: ou=people
      search-filter: "(objectClass=inetOrgPerson)"
      identity-attribute: entryUUID
      username-attribute: uid
      email-attribute: mail
      member-of-attribute: memberOf
      authority-attributes: [department, employeeType]

    group:
      base-dn: ou=groups
      search-filter: "(member={0})"
      name-attribute: cn

    sync:
      page-size: 500
      full-cron: "0 0 2 * * *"
      incremental-cron: "-"
```

LDAP group authorities use `source-system: LDAP` and `authority-type: GROUP`. Configured user
attributes use `authority-type: ATTRIBUTE` and a value such as `department=Payroll`. Both map only
to local Roles or PermissionGroups. Synchronization writes only `IDENTITY_SYNC` assignments and
preserves `MANUAL` and `SEED` assignments. Full, targeted, and scheduled synchronization use local
database locking, audit, and post-commit cache invalidation; request-time authorization remains
local.

For Active Directory, configure `identity-attribute: objectGUID` together with
`identity-attribute-binary: true`. Binary IDs are represented locally as unpadded base64url. The
application still owns Spring Security authentication and must resolve the authenticated principal
to the same configured LDAP issuer and stable subject. See
[the LDAP integration guide](docs/20-ldap-integration.md).

## Production observability and multi-instance caches

When a Micrometer `MeterRegistry` bean is available, core records authorization decisions and
latency, cache hits/misses, identity-event processing, and cache invalidations. The tags are bounded
enums/categories; user IDs, paths, and permission codes are never metric tags. Add Spring Boot
Actuator and the registry/exporter appropriate for the deployment to expose them.

Local caches are the default. Applications with multiple instances sharing one authorization
database can enable the built-in database invalidation transport:

```yaml
authorization:
  distributed-invalidation:
    enabled: true
    instance-id: ${HOSTNAME}
    poll-interval: 1s
    retention: 24h
    batch-size: 500
```

Each instance must have a unique, stable `instance-id`. A custom transport can implement the
provider-neutral outbound publisher and deliver inbound events to the supplied cache invalidator;
the database option covers both directions by default. Seed startup is serialized across instances
with a database lock, while unchanged checksums keep repeat startup idempotent. See
[the Phase 7 hardening summary](docs/22-phase-7-implementation.md).

## Run and verify

Requires Java 21. The repository-level Maven Wrapper pins Maven 3.9.11 for every module, so a separate Maven installation is not required.

```bash
./mvnw clean verify
./mvnw -pl examples/authorization-demo -am package
```

On Windows, use `mvnw.cmd` in place of `./mvnw`. Run the wrapper from the repository root so all modules use the same reactor and Maven version.

The runnable demo uses the bundled `employee-demo` Keycloak realm by default. Start its supporting
services, then launch the application:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml up -d
java -jar examples/authorization-demo/target/authorization-demo-0.1.0-SNAPSHOT.jar
```

Open:

```text
http://localhost:8080/demo-ui/
```

Sign in as `viewer`, `manager`, or `admin-user` with password `demo`. The browser redirects
to Keycloak using Authorization Code/OIDC login. On success, core places access and refresh tokens
in HttpOnly cookies, removes the temporary login session, performs targeted identity
synchronization, and authorizes from the local database/cache. The demo page checks the opaque UI
resources `seePage1` and `seePage2`; `admin-user` can open `/authorization-admin/`.

Sign out is a CSRF-protected POST and uses OIDC RP-initiated logout to clear local cookies and end
the Keycloak SSO session. See
[the demo Keycloak setup](docs/14-demo-application.md#keycloak-profile).

## Current status

Phases 1 through 7 are implemented. See [TASKS.md](TASKS.md) for the phased record,
[docs/19-phase-4-implementation.md](docs/19-phase-4-implementation.md) for Keycloak, and
[docs/20-ldap-integration.md](docs/20-ldap-integration.md) for LDAP. Phase 6 event refresh is
summarized in [docs/21-phase-6-implementation.md](docs/21-phase-6-implementation.md), and production
hardening in [docs/22-phase-7-implementation.md](docs/22-phase-7-implementation.md).
