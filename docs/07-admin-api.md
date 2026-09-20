# Admin REST API

The backend admin API is part of the optional `authorization-admin` module, which depends on `authorization-core`.

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

## Current authenticated user

```text
GET /current-user
```

Returns the resolved issuer/subject and available username, email, first name, and last name used by
the SPA header. It requires an authenticated identity and uses local synchronized attributes when a
matching user exists.

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

The collection endpoint also accepts an optional `resourceType` filter. The URL inventory endpoint
shows the Spring MVC routes registered in the current application context and combines ordered
Spring Security policy metadata with the effective enabled resource rule for each declared
method/path mapping:

```text
GET /resource-inventory/urls
```

It supports the common pagination/search parameters plus `coverage`, `accessMode`, `origin`, and
`enforcementSource` filters. A direct filter-chain decision is `MATCHED` and reports
`SECURITY_FILTER_CHAIN`, its policy code, and its decision. A policy that delegates to
`RESOURCE_RULES` reports the winning rule, or `UNMATCHED` and default denial when no rule matches.
Rule conflicts and routes without any declared security-policy metadata are `INDETERMINATE`; the
latter use the `UNKNOWN` enforcement source rather than being mislabeled as default denied.

Core contributes metadata for its default security chains. An application-defined
`SecurityFilterChain` is a complete override, so an application using the inventory should also
provide an ordered `UrlSecurityPolicyContributor` that mirrors its chain and matcher decisions.
The contributor is descriptive metadata only; Spring Security and the authorization manager remain
the enforcement mechanisms. Route metadata is protected by the dedicated
`URL:AUTHZ_RESOURCE_INVENTORY_VIEW` permission.

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

The current API does not create or edit user identity records. DB-local users enter through seed or
full snapshot import; the UI/API manages their role and permission-group assignments. The framework
does not manage passwords.

## External mappings

```text
GET    /external-mappings
POST   /external-mappings
GET    /external-mappings/{id}
PUT    /external-mappings/{id}
DELETE /external-mappings/{id}
```

Supported provider authority conventions include `KEYCLOAK` `GROUP`/`ROLE` and LDAP
`GROUP`/`ATTRIBUTE`. The contract remains open to custom source-system and authority-type strings;
all targets are restricted to local Roles or PermissionGroups.

## Sync endpoints

The status endpoint is always available for capability-driven clients. Action endpoints execute only when the selected provider reports synchronization support; otherwise they return HTTP 501:

```text
GET  /sync/status
POST /sync/full
POST /sync/incremental
POST /sync/users/{subject}?issuer={issuer}
```

The `issuer` query parameter defaults to `external` when omitted.

Protect execution with `URL:AUTHZ_SYNC_RUN`.

## Data transfer

```text
POST /data/export
POST /data/import
```

Export returns a versioned JSON attachment containing permissions, groups, roles, relationships,
resource rules, users and their assignments, pending assignments, and external mappings. Import
accepts only a complete supported-version bundle, validates it before mutation, and transactionally
replaces all portable authorization data. It is deliberately not a merge. Audit events, migration
and seed history, synchronization state, invalidation events, caches, and credentials are excluded.

Protect export with `URL:AUTHZ_DATA_EXPORT` and replacement import with
`URL:AUTHZ_DATA_IMPORT`.

## Explain/test authorization

```text
POST /authorization-test
```

Request:

```json
{
  "identity": {"issuer": "local", "subject": "michael"},
  "method": "PUT",
  "path": "/demo/employees/1"
}
```

`resourceType` defaults to `URL`. For a UI resource, send `resourceType: "UI"` and the opaque
identifier in `pattern` instead of `method` and `path`. The current SPA exposes the URL form; the
backend contract supports both built-in resource types.

Response should show safe explanation:

```json
{
  "decision": "GRANTED",
  "reason": "MATCHING_PERMISSION",
  "matchedRule": "DEMO_API",
  "matchedPermission": "URL:EMPLOYEE_EDIT",
  "assignmentPath": [
    "USER:michael",
    "ROLE:HR_MANAGER",
    "PERMISSION_GROUP:EMPLOYEE_MANAGEMENT_ACCESS",
    "PERMISSION:URL:EMPLOYEE_EDIT"
  ]
}
```

## Audit

```text
GET /audit?eventKind=DECISION|CHANGE
```

Support pagination and filtering by `eventKind`, `eventType`, actor, and target. `eventKind` is the stable record-shape discriminator; `eventType` remains the specific event name.

## Optimistic locking

Mutable configuration DTOs include `version`. Entity update requests and configuration DELETE
requests (through a `version` query parameter) provide the current version. Stale update -> HTTP
409. Relationship mapping endpoints and user assignment endpoints do not accept a client version;
they operate against the mapping's current database state and assignment-source ownership.

## Admin authorization

Protect each API with framework permissions, not hardcoded `hasRole("ADMIN")`.

UI delivery permissions are exact and namespace-specific: the UI root, runtime config, and static
assets are distinct from API operations. In particular, no UI permission uses a broad
`GET:/authorization-admin/**` pattern that could also match the API. `current-user` has its own
explicit API permission, and every admin capability family has unauthenticated and non-admin
regression coverage.


## Implemented behavior

Collection endpoints accept `page`, `size`, `sort`, and `search`; audit additionally accepts
`eventKind`, `eventType`, `actor`, and `target`. Page sizes are bounded to 1-100.

Configuration DELETE operations soft-disable roles, permission groups, permissions, and resource
rules. External mappings are explicitly deleted. User assignment endpoints create `MANUAL`
assignments and reject removal of `SEED` or `IDENTITY_SYNC` assignments.

All successful writes commit before cache invalidation and authorization-change audit publication through the core audit SPI. The admin API is
registered by `AuthorizationAdminAutoConfiguration` from `authorization-admin`, so consumers do not need to scan framework packages. Applications that include only `authorization-core` expose no management endpoints.
