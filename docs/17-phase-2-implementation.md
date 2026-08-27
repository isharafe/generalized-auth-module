# Phase 2 Implementation

## Delivered

Phase 2 provides the functional admin REST API in the optional `authorization-admin` module. The module depends on `authorization-core`, registers through explicit Spring Boot auto-configuration, and remains configurable under `authorization.admin.api`. Applications using only core expose no management API.

The API includes:

- provider capabilities
- paginated/searchable users, roles, permission groups, permissions, resource rules, external
  mappings, and audit events
- role, permission-group, permission, resource-rule, and external-mapping CRUD
- role-to-permission-group and permission-group-to-permission mappings
- MANUAL user role and permission-group assignments with assignment-source ownership enforcement
- effective entitlement queries
- safe authorization test/explain results with assignment paths
- provider-neutral synchronization status/action endpoints
- optimistic locking and consistent 400/404/409 error responses

## Write semantics

Admin controllers delegate to `AuthorizationAdminService`; controllers never access repositories
directly. Configuration codes are immutable. DELETE soft-disables coded configuration, while
external mappings are deleted explicitly. Updates and deletes require the current version, and stale
versions return HTTP 409.

User assignment writes create only `MANUAL` assignments. The API refuses to remove `SEED` or
`IDENTITY_SYNC` assignments.

Successful writes follow:

```text
validate -> transaction -> flush/version -> commit -> cache invalidation -> audit
```

User assignment changes invalidate only that stable `(issuer, subject)` cache entry. Broader
configuration changes invalidate the applicable shared caches. Admin audit persistence runs in an
independent transaction after the business transaction commits.

## Security

The admin module's seed contributor assigns resource-specific URL patterns to the `AUTHZ_*_VIEW` and `AUTHZ_*_MANAGE` permissions. Admin APIs are therefore protected through the core authorization engine like application endpoints; there is no hardcoded administrator role check.

The database source reports synchronization as unsupported. Its sync status endpoint remains
available for capability-driven clients, while action endpoints return HTTP 501 until a supporting
provider such as the Phase 4 Keycloak module is installed.

## Verification

The demo integration suite covers authentication and authorization, configuration CRUD, relationship
mappings, user assignment ownership, pagination/search, validation, optimistic conflicts,
authorization explain, immediate post-commit cache invalidation, audit persistence, and the
unsupported database synchronization facade.
