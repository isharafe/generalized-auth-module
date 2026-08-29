# Product Scope

## Goal

Build one reusable Spring Boot authorization component that is simple to consume but supports multiple identity sources.

Published modules:

```text
authorization-core
authorization-keycloak
authorization-ldap
authorization-admin
```

## Default use case

Database-backed authorization is the default and requires only `authorization-core`.

## Optional integrations

- Keycloak external identity/authority source through `authorization-keycloak`
- LDAP external identity/authority source through `authorization-ldap`
- Management REST API and built-in admin SPA through `authorization-admin`

Future identity sources should be possible through core SPIs without redesigning the engine.

## Core abstraction

```text
SecuredResource / ResourceRule = LOCK
Permission                     = KEY
```

Roles and permission groups organize keys.

## Ownership boundary

Application-side configuration is authoritative for:

- Roles
- PermissionGroups
- Permissions
- ResourceRules
- Role -> PermissionGroup
- PermissionGroup -> Permission

External systems may supply user identity and external group/role membership which are mapped into local user assignments.

## Non-goals for MVP

- building an IAM/password platform
- replacing Spring Security authentication
- Keycloak Authorization Services as the main PDP
- per-request Keycloak Admin API calls
- direct user -> permission assignment
- requiring consumers to write authorization SQL
- requiring the admin UI
