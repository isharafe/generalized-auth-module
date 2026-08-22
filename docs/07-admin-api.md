# Admin REST API

The backend admin API is part of `authorization-core`.

Default base path:

```text
/authorization-admin/api
```

Make it configurable.

## Architecture

```text
Controller
 -> Admin Service
 -> validation/business rules
 -> transaction
 -> repositories
 -> commit
 -> cache invalidation
 -> audit
```

Never make controller -> repository the primary write path.

## Capabilities

```text
GET /capabilities
```

Example DB response:

```json
{
  "source": "DATABASE",
  "identitySynchronization": false,
  "externalAuthorityMapping": true,
  "syncProvider": null
}
```

Keycloak response can report synchronization support.

## Roles

```text
GET    /roles
POST   /roles
GET    /roles/{code}
PUT    /roles/{code}
DELETE /roles/{code}
PUT    /roles/{code}/permission-groups/{groupCode}
DELETE /roles/{code}/permission-groups/{groupCode}
GET    /roles/{code}/users
```

Prefer disable over hard delete when referenced.

## Permission groups

```text
GET    /permission-groups
POST   /permission-groups
GET    /permission-groups/{code}
PUT    /permission-groups/{code}
DELETE /permission-groups/{code}
PUT    /permission-groups/{code}/permissions/{permissionCode}
DELETE /permission-groups/{code}/permissions/{permissionCode}
```

## Permissions

```text
GET    /permissions
POST   /permissions
GET    /permissions/{code}
PUT    /permissions/{code}
DELETE /permissions/{code}
```

## Resource rules

```text
GET    /resource-rules
POST   /resource-rules
GET    /resource-rules/{code}
PUT    /resource-rules/{code}
DELETE /resource-rules/{code}
```

Server validates conflicts.

## Users

At minimum:

```text
GET /users
GET /users/{id}
PUT /users/{id}/roles/{roleCode}
DELETE /users/{id}/roles/{roleCode}
PUT /users/{id}/permission-groups/{groupCode}
DELETE /users/{id}/permission-groups/{groupCode}
GET /users/{id}/effective-permissions
```

For externally synchronized users:

- identity attributes are read-only locally
- MANUAL application role/group assignments are allowed
- synchronized assignment source is visible

DB-local user creation/edit can be supported when configured, but do not turn the framework into a password-management IAM product.

## External mappings

```text
GET    /external-mappings
POST   /external-mappings
GET    /external-mappings/{id}
PUT    /external-mappings/{id}
DELETE /external-mappings/{id}
```

## Sync endpoints

The status endpoint is always available for capability-driven clients. Action endpoints execute only when the selected provider reports synchronization support; otherwise they return HTTP 501:

```text
GET  /sync/status
POST /sync/full
POST /sync/incremental
POST /sync/users/{identityReference}
```

Protect execution with `AUTHZ_SYNC_RUN`.

## Explain/test authorization

```text
POST /authorization-test
```

Request:

```json
{
  "identity": {"issuer": "local", "subject": "manager"},
  "method": "PUT",
  "path": "/demo/employees/1"
}
```

Response should show safe explanation:

```json
{
  "decision": "GRANTED",
  "reason": "MATCHING_PERMISSION",
  "matchedRule": "DEMO_API",
  "matchedPermission": "EMPLOYEE_EDIT",
  "assignmentPath": [
    "USER:manager",
    "ROLE:HR_MANAGER",
    "PERMISSION_GROUP:EMPLOYEE_MANAGER",
    "PERMISSION:EMPLOYEE_EDIT"
  ]
}
```

## Audit

```text
GET /audit
```

Support pagination/filtering.

## Optimistic locking

Mutable DTOs include `version`. PUT request bodies and DELETE requests (through a `version` query parameter) provide the current version. Stale update -> HTTP 409.

## Admin authorization

Protect each API with framework permissions, not hardcoded `hasRole("ADMIN")`.


## Implemented behavior

Collection endpoints accept `page`, `size`, `sort`, and `search`; audit additionally accepts
`eventType`, `actor`, and `target`. Page sizes are bounded to 1-100.

Configuration DELETE operations soft-disable roles, permission groups, permissions, and resource
rules. External mappings are explicitly deleted. User assignment endpoints create `MANUAL`
assignments and reject removal of `SEED` or `IDENTITY_SYNC` assignments.

All successful writes commit before cache invalidation and admin audit publication. The admin API is
registered by `AuthorizationAdminAutoConfiguration`, so consumers do not need to scan framework
packages.
