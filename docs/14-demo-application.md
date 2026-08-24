# Runnable Demo Application

Path:

```text
examples/authorization-demo/
```

This example is not a published reusable framework module. It proves the framework works end-to-end.

## Default profile

Use:

```text
H2
authorization.source=database
```

## Demo-only authentication

Provide a clearly development-only authentication mechanism enabled only under a `demo` profile.

A simple option is a custom demo filter/header:

```text
X-Demo-User: viewer
X-Demo-User: manager
```

Map to stable identities:

```text
local | viewer
local | manager
```

This impersonation mechanism must never be part of production framework behavior.

## Seed data

Users:

```text
viewer
manager
```

Roles:

```text
HR_VIEWER
HR_MANAGER
```

Permission groups:

```text
EMPLOYEE_VIEWER
EMPLOYEE_MANAGER
```

Permissions:

```text
EMPLOYEE_VIEW | GET:/demo/employees/**
EMPLOYEE_EDIT | PUT:/demo/employees/**
```

Mappings:

```text
viewer  -> HR_VIEWER  -> EMPLOYEE_VIEWER -> EMPLOYEE_VIEW
manager -> HR_MANAGER -> EMPLOYEE_MANAGER -> EMPLOYEE_VIEW + EMPLOYEE_EDIT
```

For demo administration, manager may also receive framework role `AUTHZ_SYSTEM_ADMIN` through application seed data.

## Resource rules

```text
/demo/public/**                PERMIT_ALL
/demo/profile/**               AUTHENTICATED
/demo/employees/**             AUTHORIZED
/authorization-admin/api/**    AUTHORIZED
/authorization-admin/**        AUTHORIZED when UI enabled
```

Admin API permissions themselves must enforce finer-grained admin operations.

## Endpoints

```text
GET /demo/public
GET /demo/profile
GET /demo/employees
PUT /demo/employees/{id}
```

Employee responses can be simple in-memory DTOs; no employee domain DB is required.

## Expected behavior

No user:

```text
GET /demo/public       -> 200
GET /demo/profile      -> 401
GET /demo/employees    -> 401
```

Viewer:

```text
GET /demo/profile          -> 200
GET /demo/employees        -> 200
PUT /demo/employees/1      -> 403
```

Manager:

```text
GET /demo/employees        -> 200
PUT /demo/employees/1      -> 200
```

## Admin API

Manager/admin can manage data through admin REST APIs according to seeded framework admin permissions.

## Admin UI

The demo includes `authorization-admin-ui` and serves it from `/authorization-admin/`. Open:

```text
http://localhost:8080/authorization-admin?demo-user=manager
```

The demo-only authentication filter converts that query parameter into a one-hour HttpOnly cookie and redirects to the clean UI URL, allowing same-origin API requests to remain authenticated. Header authentication remains available for curl and tests. Neither demo mechanism is suitable for production.

## Keycloak profile

Later add a `keycloak-demo` profile that:

- removes demo header auth
- enables Spring Resource Server JWT
- sets `authorization.source=keycloak`
- documents required Keycloak client/realm setup
