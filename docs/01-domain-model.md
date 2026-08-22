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
id
code
resourceType
resourcePattern
accessMode
priority
enabled
version
```

Access modes:

```text
PERMIT_ALL
AUTHENTICATED
AUTHORIZED
DENY_ALL
```

## Permission

Fields:

`Permission` is resource-type-neutral. Its `resourcePattern` is opaque to the domain model and is
interpreted only by the matcher for the corresponding `ResourceType`.


```text
id
code
name
description
resourceType
resourcePattern
enabled
version
```

Example:

```text
code            = EMPLOYEE_EDIT
type            = URL
resourcePattern = PUT:/api/employees/**
```

## PermissionGroup

Logical collection of permissions.

```text
EMPLOYEE_MANAGEMENT
├── EMPLOYEE_VIEW
├── EMPLOYEE_CREATE
├── EMPLOYEE_EDIT
└── EMPLOYEE_DELETE
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
externalIssuer
externalSubject
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
(externalIssuer, externalSubject)
```

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
