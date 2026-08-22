# Acceptance Criteria

The implementation is acceptable only when all criteria for completed phases are met.

## Build

From repository root:

```text
mvn clean verify
```

passes.

Frontend build is integrated when `authorization-admin-ui` is built.

## Module count

Published reusable modules are exactly:

```text
authorization-core
authorization-keycloak
authorization-admin-ui
```

Do not create extra framework modules for Spring/JPA/Flyway/admin API.

## DB-only

`authorization-core` works without Keycloak or UI dependencies.

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

## Admin API

Functional CRUD/mapping/explain/audit endpoints.

Writes validate, transact, invalidate caches after commit, and audit.

## Admin UI

Optional dependency only.

Functional SPA uses admin REST API and capabilities endpoint.

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
- admin API
- optional UI dependency
- optional Keycloak dependency/configuration
- demo run commands
