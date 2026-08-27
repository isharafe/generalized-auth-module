# Acceptance Criteria

The implementation is acceptable only when all criteria for completed phases are met.

## Build

From repository root:

```text
./mvnw clean verify
```

passes.

Frontend build is integrated when `authorization-admin` is built.

## Module count

Published reusable modules are exactly:

```text
authorization-core
authorization-keycloak
authorization-admin
```

Do not create extra framework modules for Spring/JPA/Flyway. Keep the management API and UI together in `authorization-admin`.

## DB-only

`authorization-core` works without Keycloak or admin dependencies and exposes no management endpoints.

Demo proves end-to-end DB-only authorization.

## Security

- fail closed
- no rule -> deny
- correct 401/403/503 semantics
- URL matching security tests pass
- admin endpoints protected
- secrets/tokens not logged

## Database/Flyway

- authorization schema is created by module migrations
- authorization history table is separate
- application V1 and authorization V1 coexist
- logical codes unique
- assignment source stored
- optimistic locking works

## Seed

- consumers use YAML/Java, not internal SQL
- repeated seed is idempotent
- bad seed fails startup before partial mutation
- framework admin permissions/roles exist
- consuming application can seed initial admin assignment

## Administration module

`authorization-admin` is optional and depends only on `authorization-core` among framework modules.

It provides functional CRUD/mapping/explain/audit endpoints, framework-admin seed definitions, and the SPA. Writes validate, transact, invalidate caches after commit, and audit. The frontend uses only the admin REST API and capabilities endpoint.

## Keycloak

Optional dependency only.

`source=database` works when Keycloak module is absent.

`source=keycloak` explicitly maps/syncs external groups/roles into local assignments.

Normal request authorization stays local.

## No placeholders

No unfinished production TODOs, placeholder migrations, fake implementations, or unsupported methods for features claimed complete.

## Documentation

Repository README must explain:

- core dependency
- DB-only configuration
- seed format
- optional administration dependency and API
- built-in admin UI
- optional Keycloak dependency/configuration
- demo run commands
