# REST API Contract

Use JSON DTOs. Never expose JPA entities.

## Common error model

```json
{
  "code": "AUTHZ_ROLE_NOT_FOUND",
  "message": "Role HR_UNKNOWN does not exist",
  "timestamp": "2026-08-21T00:00:00Z",
  "correlationId": "..."
}
```

Never expose stack traces to clients.

## Validation

HTTP 400.

```json
{
  "code": "AUTHZ_VALIDATION_FAILED",
  "errors": [
    {"field": "pattern", "message": "Invalid URL pattern"}
  ]
}
```

Bean-validation failures populate `errors`. Service-level validation failures use the same response
shape with a specific top-level `message` and may return an empty `errors` list.

## Optimistic locking

HTTP 409.

```json
{
  "code": "AUTHZ_CONCURRENT_MODIFICATION",
  "message": "The resource was changed by another administrator"
}
```

## Pagination

Collection APIs support at least:

```text
page
size
sort
search
```

Use bounded default/max page size. The implemented maximum is 100.

Page response:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

## Role DTO

```json
{
  "code": "HR_MANAGER",
  "name": "HR Manager",
  "description": "Manages employees",
  "enabled": true,
  "permissionGroups": ["EMPLOYEE_READ_ACCESS", "EMPLOYEE_MANAGEMENT_ACCESS"],
  "version": 3
}
```

## Permission DTO

```json
{
  "code": "URL:EMPLOYEE_EDIT",
  "name": "Edit employees",
  "resourceType": "URL",
  "pattern": "PUT:/demo/employees/**",
  "enabled": true,
  "version": 1
}
```

## Assignment DTO

Always expose source:

```json
{
  "code": "FINANCE_MANAGER",
  "source": "IDENTITY_SYNC",
  "sourceReference": "/AD/Finance-Managers"
}
```

Do not silently allow deleting an `IDENTITY_SYNC` assignment as if it were manual; the next sync would recreate it. UI/API should make source ownership clear.

## URL resource inventory DTO

`GET /resource-inventory/urls` returns the standard page envelope. Each item contains the canonical
`METHOD:/path`, separate `method` and `path` fields, `coverageStatus`, the effective `accessMode`,
matched rule code/priority, decision reason, handler origins, and handler names. Coverage statuses
are `MATCHED`, `UNMATCHED`, and `INDETERMINATE`; unmatched items have no rule/access mode and are
denied by default.

## Current-user DTO

```json
{
  "issuer": "local",
  "subject": "michael",
  "username": "michael",
  "email": "michael@example.com",
  "firstName": "Demo",
  "lastName": "Manager"
}
```

## Portable data bundle

`POST /data/export` returns JSON with `formatVersion`, `exportedAt`, and complete arrays named
`permissions`, `permissionGroups`, `roles`, `resourceRules`, `users`, `externalMappings`, and
`pendingUserAssignments`. User records contain their role and permission-group assignments,
including assignment source/reference.

`POST /data/import` requires the complete bundle and replaces portable authorization data in one
transaction after validation. It is not a partial or merge contract. The current format version is
`1`, and all permission codes and permission-group references use the canonical
`<RESOURCE_TYPE>:<LOCAL_CODE>` form.

## Deletion

Prefer soft disable for referenced authorization configuration. Roles, permission groups,
permissions, and resource rules are soft-disabled. External-authority mappings support explicit hard
delete. Those configuration DELETE requests carry the current optimistic `version` as a query
parameter. Relationship-removal and user-assignment DELETE endpoints do not take a version.
