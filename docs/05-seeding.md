# Seed Framework

## Goal

Consuming applications define logical authorization seed data without knowing SQL/table structure.

Support two input mechanisms:

1. YAML/JSON resource files
2. Java `AuthorizationSeedContributor`

Both feed the same internal `AuthorizationSeedDefinition`.

## YAML example

```yaml
authorization:
  seed:
    permissions:
      - code: URL:EMPLOYEE_VIEW
        name: View employees
        type: URL
        pattern: GET:/demo/employees/**

      - code: URL:EMPLOYEE_EDIT
        name: Edit employees
        type: URL
        pattern: PUT:/demo/employees/**

    permission-groups:
      - code: EMPLOYEE_VIEWER
        permissions:
          - URL:EMPLOYEE_VIEW

      - code: EMPLOYEE_MANAGER
        permissions:
          - URL:EMPLOYEE_VIEW
          - URL:EMPLOYEE_EDIT

    roles:
      - code: HR_VIEWER
        permission-groups:
          - EMPLOYEE_VIEWER

      - code: HR_MANAGER
        permission-groups:
          - EMPLOYEE_MANAGER

    resource-rules:
      - code: DEMO_PUBLIC
        type: URL
        pattern: "*:/demo/public"
        access-mode: PERMIT_ALL

      - code: DEMO_PROFILE
        type: URL
        pattern: "*:/demo/profile"
        access-mode: AUTHENTICATED

      - code: DEMO_API
        type: URL
        pattern: "*:/demo/employees/**"
        access-mode: AUTHORIZED
```

## Java contributor

Public contract:

```java
public interface AuthorizationSeedContributor {
    void contribute(AuthorizationSeedBuilder seed);
}
```

The fluent API expresses permissions, permission groups, roles, resource rules, external-authority
mappings, users, and initial role/group assignments through logical codes. Permissions accept a
`ResourceType`, so Java contributors can define URL or UI permissions. Use
`externalAuthorityMapping(...)` for the same mapping contract available under
`external-authority-mappings` in YAML. URL patterns use the canonical `METHOD:/path` form.
Permission codes must use `<RESOURCE_TYPE>:<LOCAL_CODE>` and group references use that complete
code; for example, `URL:EMPLOYEE_VIEW` and `UI:EMPLOYEE_VIEW` are independent permissions.

## Merge process

```text
framework seed
+
application YAML files
+
Java contributors
    |
    v
AuthorizationSeedDefinition
    |
validate everything
    |
transactional MERGE
```

## MERGE semantics

Default:

- create missing objects
- update safe descriptive fields
- replace permission membership for each supplied permission group
- replace permission-group membership for each supplied role
- create missing user assignments without removing existing assignments
- merge external-authority mappings by their source/authority/target natural key
- never duplicate rows
- do not delete top-level configuration merely because it is absent from a seed file

A future authoritative RECONCILE seed mode can be added later, but is not MVP default.

## Validation

Fail startup by default on:

- duplicate codes
- missing referenced permission/group/role
- invalid HTTP method
- invalid resource type
- malformed path pattern
- invalid access mode
- missing or unknown external-authority mapping targets
- identical resource-rule type/pattern pairs with equal priority but conflicting access modes

Validate before any mutation. More general runtime ambiguities that cannot be detected by the seed
validator produce an `INDETERMINATE` authorization decision rather than an unsafe grant.

## Framework-owned admin seed

When `authorization-admin` is present, that module contributes built-in permissions such as:

```text
URL:AUTHZ_ADMIN_VIEW
URL:AUTHZ_ADMIN_CURRENT_USER
URL:AUTHZ_ADMIN_UI
URL:AUTHZ_ADMIN_UI_INDEX
URL:AUTHZ_ADMIN_UI_CONFIG
URL:AUTHZ_ADMIN_UI_ASSETS
URL:AUTHZ_USER_VIEW
URL:AUTHZ_USER_MANAGE
URL:AUTHZ_ROLE_VIEW
URL:AUTHZ_ROLE_MANAGE
URL:AUTHZ_PERMISSION_VIEW
URL:AUTHZ_PERMISSION_MANAGE
URL:AUTHZ_PERMISSION_GROUP_VIEW
URL:AUTHZ_PERMISSION_GROUP_MANAGE
URL:AUTHZ_RESOURCE_RULE_VIEW
URL:AUTHZ_RESOURCE_RULE_MANAGE
URL:AUTHZ_EXTERNAL_MAPPING_VIEW
URL:AUTHZ_EXTERNAL_MAPPING_MANAGE
URL:AUTHZ_SYNC_VIEW
URL:AUTHZ_SYNC_RUN
URL:AUTHZ_AUDIT_VIEW
URL:AUTHZ_AUTHORIZATION_TEST
URL:AUTHZ_DATA_EXPORT
URL:AUTHZ_DATA_IMPORT
```

Create framework groups/roles:

```text
AUTHZ_SYSTEM_VIEWER
AUTHZ_SYSTEM_ADMIN
```

Never hardcode a user into these roles. The consuming app assigns an initial administrator through seed configuration.

## Pending user assignment

If a seed references a user that does not yet exist (for example before Keycloak sync), persist a pending assignment and resolve it when that identity is created.

Prefer `(issuer, subject)` over username.

## Seed history

Record source/checksum/status/applied time in `AUTH_SEED_HISTORY`.

Unchanged seed can be skipped where safe.

## Multi-pod safety

The implementation combines:

- unique constraints
- transactions
- idempotent operations
- a pessimistically locked `AUTH_SYNC_STATE.GLOBAL_SEED_INITIALIZATION` row

The complete seed is validated before the lock-protected mutation. Concurrent-startup integration
tests run multiple callers against the same database and verify one consistent result and history
record.
