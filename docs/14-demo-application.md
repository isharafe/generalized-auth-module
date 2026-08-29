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
/demo/public                   PERMIT_ALL
/demo/profile                  AUTHENTICATED
/demo/employees/**             AUTHORIZED
/authorization-admin/api/**    AUTHORIZED
/authorization-admin/**        AUTHORIZED when UI enabled
```

Admin API permissions themselves must enforce finer-grained admin operations.

The integration suite treats the UI and API namespaces independently and exercises path parsing
through the actual filter chain, including context paths, encoded/repeated separators, semicolon
parameters, trailing slashes, and method-override headers.

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

The demo includes `authorization-admin` and serves it from `/authorization-admin/`. Open:

```text
http://localhost:8080/authorization-admin?demo-user=manager
```

The demo-only authentication filter authenticates the request and converts that query parameter into a one-hour HttpOnly cookie. The admin UI controller then redirects the no-trailing-slash URL to the clean `/authorization-admin/` URL, allowing same-origin API requests to remain authenticated. Header authentication remains available for curl and tests. Neither demo mechanism is suitable for production.

## Keycloak profile

The `keycloak-demo` profile replaces demo header authentication with:

- Spring Security OAuth2/OIDC browser login
- JWT Resource Server support for bearer-token API calls
- `authorization.source=keycloak`
- targeted identity synchronization after successful browser login

### Keycloak clients

Create two confidential clients in realm `authorization-demo`.

Synchronization client:

```text
Client ID: authorization-sync-service
Client authentication: On
Service accounts: Enabled
```

Grant its service account the narrow `realm-management` permissions needed to query users, groups,
and realm-role mappings.

Browser-login client:

```text
Client ID: authorization-demo-web
Client authentication: On
Standard flow: Enabled
Valid redirect URI: http://localhost:8080/login/oauth2/code/keycloak
Valid post logout redirect URI: http://localhost:8080/demo-ui/signed-out
```

Keeping login and synchronization clients separate prevents the browser-login client from receiving
administrative service-account privileges.

### Demo permissions

The seed defines two opaque UI resources:

```text
UI:seePage1
UI:seePage2
```

`EMPLOYEE_VIEWER` contains `UI:seePage1`. `EMPLOYEE_MANAGER` contains both permissions. Existing
Keycloak group/realm-role mappings therefore produce this browser behavior:

```text
viewer  -> page 1 only
manager -> page 1 and page 2
```

Assign a test user to `/authorization-demo/viewers` (or realm role
`authorization-demo-viewer`) for page 1 only. Assign another user to
`/authorization-demo/employee-managers` (or realm role `authorization-demo-manager`) for both
pages. The login-time synchronization converts those external authorities into the local seeded
roles; Keycloak does not define the UI permissions directly.

The landing page hides unavailable links, and each page checks its UI permission again on direct
navigation. The containing `/demo-ui/**` URL resource remains `AUTHENTICATED`, demonstrating that
URL access and UI-component authorization are independent concerns.

### Run

Build/install the reactor dependencies first:

```bash
./mvnw -pl examples/authorization-demo -am install -DskipTests
```

Then run only the application module so Maven does not try to execute the parent POM:

```bash
SPRING_PROFILES_ACTIVE=keycloak-demo \
AUTHORIZATION_KEYCLOAK_CLIENT_SECRET='<synchronization-client-secret>' \
AUTHORIZATION_KEYCLOAK_LOGIN_CLIENT_SECRET='<browser-login-client-secret>' \
./mvnw -pl examples/authorization-demo spring-boot:run
```

Defaults expect Keycloak at `http://localhost:8081`, realm `authorization-demo`, synchronization
client `authorization-sync-service`, browser client `authorization-demo-web`, and issuer
`http://localhost:8081/realms/authorization-demo`. Override these with
`AUTHORIZATION_KEYCLOAK_BASE_URL`, `AUTHORIZATION_KEYCLOAK_REALM`,
`AUTHORIZATION_KEYCLOAK_CLIENT_ID`, `AUTHORIZATION_KEYCLOAK_LOGIN_CLIENT_ID`, and
`AUTHORIZATION_KEYCLOAK_ISSUER_URI`.

Open:

```text
http://localhost:8080/demo-ui/
```

An unauthenticated HTML request redirects to Keycloak. After login, the success handler synchronizes
that exact `(issuer, subject)` before redirecting to the demo landing page, so no curl/bootstrap sync
is required. A synchronization failure returns HTTP 503 instead of treating the user as having no
permissions.

The Sign out link performs OIDC RP-initiated logout, ending both the local application session and
the Keycloak SSO session before returning to the public `/demo-ui/signed-out` page.

The default demo query/header authentication is unavailable in this profile. Bearer-token curl or
Postman calls remain supported for API testing, but are not required for the sample pages.
