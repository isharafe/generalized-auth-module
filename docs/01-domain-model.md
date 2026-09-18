# Domain Model

## ResourceType

Resource types are represented by the shared enum:

```text
URL
UI
```

URL patterns use `METHOD:/path` with Spring path matching. UI patterns are opaque identifiers
matched exactly. Matching, validation, and specificity are delegated through a strategy registry
keyed by `ResourceType`; additional types add a `ResourcePatternMatcher` strategy.

## ResourceRule

Fields:

```text
code
resourceType
pattern
accessMode
priority
enabled
```

The JPA entity adds an internal numeric `id` and optimistic `version`; the public domain record does
not expose either value. Admin API DTOs expose `version`, but continue to identify rules by code.

Access modes:

```text
PERMIT_ALL
AUTHENTICATED
AUTHORIZED
DENY_ALL
```

## Permission

Fields:

`Permission` carries its resource type, while its `pattern` remains opaque to the domain model and
is interpreted only by the matcher for the corresponding `ResourceType`.

```text
code
name
description
resourceType
pattern
enabled
```

The persistence entity adds an internal numeric `id` and optimistic `version`. The admin API exposes
`version`, but not the internal ID.

Example:

```text
code            = URL:EMPLOYEE_EDIT
resourceType    = URL
pattern         = PUT:/api/employees/**
```

## PermissionGroup

Logical collection of permissions.

```text
EMPLOYEE_MANAGEMENT
├── URL:EMPLOYEE_VIEW
├── URL:EMPLOYEE_CREATE
├── URL:EMPLOYEE_EDIT
└── URL:EMPLOYEE_DELETE
```

## Role

Logical collection of permission groups.

```text
HR_MANAGER
├── EMPLOYEE_MANAGEMENT
└── EMPLOYEE_REPORTING
```

## ApplicationUser

Recommended fields:

```text
id
issuer
subject
username
email
firstName
lastName
enabled
externalDirectoryId
lastIdentitySyncAt
identitySyncStatus
entitlementVersion
version
```

Stable identity key:

```text
(issuer, subject)
```

The Java entity and REST DTO call these values `issuer` and `subject`; the database columns retain
the explicit names `EXTERNAL_ISSUER` and `EXTERNAL_SUBJECT`.

## Associations

Support:

```text
User -> Role
User -> PermissionGroup
Role -> PermissionGroup
PermissionGroup -> Permission
```

Do not add direct User -> Permission in MVP.

## Assignment source

User mappings store:

```text
SEED
IDENTITY_SYNC
MANUAL
```

and an optional `sourceReference`.

Example:

```text
John -> FINANCE_MANAGER -> IDENTITY_SYNC -> /AD/Finance-Managers
John -> REPORT_AUDITOR  -> MANUAL
```

Identity sync may remove only the first.

## ExternalAuthorityMapping

Fields:

```text
sourceSystem
authorityType
authorityValue
targetType
targetCode
enabled
version
```

Supported target types:

```text
ROLE
PERMISSION_GROUP
```

Examples:

```text
KEYCLOAK | GROUP | /AD/Finance-Managers | ROLE | FINANCE_MANAGER
KEYCLOAK | ROLE  | payroll-approver     | ROLE | PAYROLL_APPROVER
LDAP     | GROUP | Finance-Managers     | ROLE | FINANCE_MANAGER
LDAP     | ATTRIBUTE | department=Payroll | PERMISSION_GROUP | PAYROLL_ACCESS
```

## Effective permissions

Union:

```text
User direct PermissionGroups -> Permissions
+
User Roles -> PermissionGroups -> Permissions
```

Deduplicate by permission code.

## Stable codes

`code` is the public/configuration identifier.

Rules:

- unique
- machine-readable
- immutable by default after creation
- seed/API references use code
- DB numeric IDs stay internal

Permission codes additionally use the canonical `<RESOURCE_TYPE>:<LOCAL_CODE>` form. The prefix
must equal the permission's `resourceType`, the complete code is limited to 100 characters, and
both code and type are immutable after creation. This allows `URL:VIEW` and `UI:VIEW` to coexist
while preserving a single-string public identifier. `PermissionCode` provides shared composition
and validation for Java consumers.
