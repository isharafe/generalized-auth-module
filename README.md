# Authorization Framework for Spring Boot

Application-owned authorization for Spring Boot 4.1 applications, with DB-backed decisions,
optional Keycloak and LDAP synchronization, a packaged administration console, and Nuxt/Nitro
integration.

Keep roles, permission groups, permissions, and resource rules close to the application while your
identity provider continues to own identities and external authorities. Normal authorization
requests are evaluated from the local cache and database—never through a request-time Keycloak or
LDAP call—and ambiguous or unavailable authorization state fails closed.

**[Get started](#add-the-db-backed-core)** · **[Run the demo](#run-and-verify)** ·
**[Explore the modules](#modules)** · **[Sponsor the project ❤️](https://github.com/sponsors/isharafe)**

## Why this project?

- **Keep authorization application-owned.** External groups and roles map explicitly to local
  roles and permission groups; an identity provider never defines application URL permissions.
- **Start small and add only what you need.** Use the DB-backed core by itself, then opt into the
  admin console, Keycloak synchronization, LDAP synchronization, or Nuxt integration.
- **Use Spring Security end to end.** A native `AuthorizationManager` provides fail-closed resource
  rules, Spring path matching, and distinct 401, 403, and 503 outcomes.
- **Operate it with confidence.** Separate Flyway migrations, idempotent seeds, targeted caching,
  audit hooks, optimistic locking, metrics, and multi-instance invalidation are included.
- **Try real workflows.** Runnable Spring and Spring + Nuxt demos cover public, authenticated,
  permission-controlled, administration, and Keycloak-backed synchronization flows.

## Why not just connect Keycloak directly?

You can. For a small application with a few stable roles, reading Keycloak roles from a token and
writing a handful of checks may be all you need.

But Keycloak mainly tells your application **who the user is** and which external roles or groups
they have. Your application still has to decide **what that user may do**. As requirements grow,
teams often end up rebuilding the same authorization code in every application:

| When wiring Keycloak directly | With this framework |
| --- | --- |
| Write code to translate Keycloak roles and groups into application roles. | Map external authorities to local roles or permission groups through seed configuration or the admin UI. |
| Add role checks throughout controllers and services. | Protect URL resources centrally through Spring Security's authorization pipeline. |
| Create broader and broader roles—or many new roles—for every access variation. | Grant focused permissions such as `URL:EMPLOYEE_VIEW` and `URL:EMPLOYEE_EDIT`. |
| Repeat role-based `if` and `v-if` checks throughout the frontend. | Use `<Authorized>`, `v-authorization`, and a permission-aware composable for UI behavior. |
| Build synchronization, caching, invalidation, audit, migrations, and management tools yourself. | Use the implementations already provided by the core and optional modules. |
| Decide how missing rules and infrastructure failures should behave. | Get tested fail-closed defaults, including distinct 401, 403, and 503 outcomes. |

Without a shared authorization layer, backend checks can easily become scattered application code:

```java
if (!userHasRole("ADMIN")) {
    throw new AccessDeniedException("Not allowed");
}
```

Frontend code often repeats the same broad role assumption:

```vue
<button v-if="user.roles.includes('ADMIN')">Edit employee</button>
```

This framework lets the application describe the actual capability instead—such as
`URL:EMPLOYEE_EDIT` for the protected operation and `UI:EMPLOYEE_EDIT` for its presentation—and
assign those permissions through reusable permission groups and roles. Backend URL rules are
enforced centrally by `AuthorizationManager`; the UI integration can then express the matching
intent without depending on an identity-provider role name:

```vue
<Authorized permission="UI:EMPLOYEE_EDIT">
  <button>Edit employee</button>
</Authorized>
```

UI checks improve the user experience but are not a security boundary. The corresponding backend
operation must always remain protected by Spring Security.

The result is less authorization plumbing to recreate in each application and a consistent set of
defaults: no matching rule denies access, provider failures do not look like empty permissions,
Spring path matching is used, synchronized assignments cannot overwrite manual or seeded ones, and
the optional cookie flow provides HttpOnly tokens and CSRF protection. Keycloak still handles
identity; this framework turns that identity into granular, application-owned permissions. See the
[runtime architecture](docs/02-runtime-architecture.md) and
[Keycloak integration](docs/09-keycloak-integration.md) for the full flow.

## Modules

- `authorization-core`: published DB-backed engine, Spring Security integration, JPA/Flyway, seeds, cache, and audit.
- `authorization-keycloak`: published optional Keycloak Admin API client and local identity/authority synchronization.
- `authorization-ldap`: published optional LDAP directory client and local identity/authority synchronization.
- `authorization-admin`: published optional management REST API, services, framework-admin seeds, and React/TypeScript SPA.
- `examples/authorization-demo`: non-published runnable verification application.
- `examples/authorization-nuxt-demo`: non-published Spring + Nuxt/Nitro browser demo, including the optional admin UI.
- `integrations/authorization-nuxt`: source-only Nuxt 3/4 module for permission-aware UI and secure backend requests.

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

Core also exposes the authenticated user's enabled `UI:*` permission codes at
`/authorization/ui/permissions`. The response is marked `no-store`, contains the entitlement
version, and never includes URL/API permissions. Set `authorization.ui-api.enabled=false` when the
application does not need a browser permission snapshot.

## Nuxt/Nitro integration

The source package in `integrations/authorization-nuxt` provides `<Authorized>`,
`v-authorization`, `useAuthorization()`, protected route middleware, and an authorized fetch client.
Its Nitro proxy keeps OAuth tokens in Spring-managed HttpOnly cookies, lazily attaches CSRF headers
to unsafe requests, serializes access-token refresh, retries a rejected request once, and reloads
the UI permission snapshot after refresh.

```ts
export default defineNuxtConfig({
  modules: ['@isharafe/authorization-nuxt'],
  authorizationNuxt: {
    backendBaseUrl: 'http://localhost:8080',
    publicBaseUrl: 'http://localhost:3000',
    loginEndpoint: '/oauth2/authorization/keycloak'
  }
})
```

```vue
<Authorized permission="UI:EMPLOYEE_VIEW">
  <EmployeeTable />
</Authorized>

<button v-authorization.disable="'UI:EMPLOYEE_EDIT'">Save</button>
```

UI controls fail closed and are presentation-only; Spring Security must still protect every
backend operation. See [the Nuxt/Nitro integration guide](docs/23-nuxt-integration.md) and the
[runnable Spring + Nuxt demo](examples/authorization-nuxt-demo/README.md).

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
      - code: EMPLOYEE_READ_ACCESS
        name: Employee read access
        permissions: [URL:EMPLOYEE_VIEW]
    roles:
      - code: HR_ANALYST
        name: HR analyst
        permission-groups: [EMPLOYEE_READ_ACCESS]
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

Alternatively, build and run the application in a container together with the identity services:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml \
  --profile authorization-demo up -d --build
```

Open:

```text
http://localhost:8080/demo-ui/
```

Sign in as `emma`, `michael`, or `olivia` with password `demo`. The browser redirects
to Keycloak using Authorization Code/OIDC login. On success, core places access and refresh tokens
in HttpOnly cookies, removes the temporary login session, performs targeted identity
synchronization, and authorizes from the local database/cache. The demo page checks the opaque UI
resources `employeeDirectory` and `managerWorkspace`; `olivia` can open
`/authorization-admin/`.

Sign out is a CSRF-protected POST and uses OIDC RP-initiated logout to clear local cookies and end
the Keycloak SSO session. See
[the demo Keycloak setup](docs/14-demo-application.md#keycloak-profile).

For the Nuxt/Nitro example, run the Spring backend on port 8082 and the browser-facing Nuxt server
on port 3000. The same demo identities exercise permission-aware components, directives, route
middleware, authorized requests, token refresh, and the proxied administration UI. Follow the
[Nuxt demo quickstart](examples/authorization-nuxt-demo/README.md).

Both Nuxt processes can also be built and launched as separate containers:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml \
  --profile authorization-nuxt-demo up -d --build
```

## Support the project

Authorization infrastructure sits on a sensitive boundary and needs ongoing attention long after
the first implementation. Maintaining this project means tracking changes across Java, Spring Boot,
Spring Security, Keycloak, LDAP, React, and Nuxt while preserving secure defaults and upgrade-safe
behavior.

If this framework saves your team from rebuilding authorization plumbing—or you want to help it
become a dependable long-term option—please consider
**[sponsoring its development](https://github.com/sponsors/isharafe)**.

Sponsorship helps create dedicated time for:

- security maintenance and regression testing;
- compatibility updates and stable releases;
- migration, synchronization, and multi-instance reliability work;
- clearer documentation and runnable examples; and
- issue investigation and long-term project stewardship.

Every sponsorship, at any level, helps turn continued maintenance from spare-time work into
predictable project investment. If sponsorship is not possible, a star, a bug report, or feedback
from a real integration is also valuable.

## Current status

Phases 1 through 8 are implemented. See [TASKS.md](TASKS.md) for the phased record,
[docs/19-phase-4-implementation.md](docs/19-phase-4-implementation.md) for Keycloak, and
[docs/20-ldap-integration.md](docs/20-ldap-integration.md) for LDAP. Phase 6 event refresh is
summarized in [docs/21-phase-6-implementation.md](docs/21-phase-6-implementation.md), and production
hardening in [docs/22-phase-7-implementation.md](docs/22-phase-7-implementation.md). The Nuxt/Nitro
client is documented in [docs/23-nuxt-integration.md](docs/23-nuxt-integration.md).
