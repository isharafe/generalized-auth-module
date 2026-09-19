# Runnable Demo Application

Path:

```text
examples/authorization-demo/
```

This example is not a published reusable framework module. It proves the framework works end-to-end.

## Default profile

The runnable application activates `keycloak-demo` by default and uses
`authorization.source=keycloak`. H2 still stores application authorization data and synchronized
identity assignments.

It uses core's generalized cookie OAuth2 security:

- OAuth2/OIDC browser login and JWT validation
- stateless normal requests with HttpOnly bearer-token cookies
- targeted synchronization after login
- OIDC logout selected by property

This Keycloak-first default intentionally supersedes the repository's original Phase 1 requirement
for a DB-only runnable default. The shipped application contains no demo header or query-parameter
authentication. The integration suite retains a test-only local header chain under the `test`
profile so the DB-backed path is verified without external services; that chain is never packaged
as runtime authentication.

## Seed and identity data

The bundled Keycloak realm provides these login identities (all use password `demo`):

```text
emma
michael
olivia
```

The application seed deliberately does not create or assign users. Targeted synchronization maps
the external authorities for those identities onto the following application-owned model.

Roles:

```text
HR_ANALYST
HR_MANAGER
AUTHORIZATION_ADMINISTRATOR
```

Permission groups:

```text
EMPLOYEE_READ_ACCESS
EMPLOYEE_MANAGEMENT_ACCESS
```

Permissions:

```text
URL:EMPLOYEE_VIEW | GET:/demo/employees/**
URL:EMPLOYEE_EDIT | PUT:/demo/employees/**
```

Mappings:

```text
emma -> HR_ANALYST -> EMPLOYEE_READ_ACCESS -> URL:EMPLOYEE_VIEW
michael -> HR_MANAGER -> EMPLOYEE_READ_ACCESS + EMPLOYEE_MANAGEMENT_ACCESS
olivia -> AUTHORIZATION_ADMINISTRATOR -> AUTHZ_SYSTEM_ADMIN
```

The isolated integration-test profile loads a second seed file with matching local test identities;
this is test fixture data, not runtime demo authentication.

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

HR analyst:

```text
GET /demo/profile          -> 200
GET /demo/employees        -> 200
PUT /demo/employees/1      -> 403
```

HR manager:

```text
GET /demo/employees        -> 200
PUT /demo/employees/1      -> 200
```

## Admin API

`olivia` can manage data through the admin REST APIs according to the seeded framework admin
permissions.

## Admin UI

The demo includes `authorization-admin` and serves it from `/authorization-admin/`. Sign in as
`olivia` through the normal Keycloak login flow, then open:

```text
http://localhost:8080/authorization-admin/
```

The SPA uses the access-token cookie for same-origin API calls. It supplies the framework CSRF
token on writes, performs one refresh-token exchange after an expired access token, and offers a
CSRF-protected logout form. The local header chain used by integration tests is active only under
the `test` profile and is not part of the packaged application.

## Keycloak profile

The default `keycloak-demo` profile provides:

- Spring Security OAuth2/OIDC browser login
- JWT Resource Server support for bearer-header and HttpOnly-cookie API calls
- `authorization.source=keycloak`
- targeted identity synchronization after successful browser login
- access-token refresh and local/OIDC logout endpoints protected by CSRF

### Keycloak clients

The bundled `employee-demo` realm already defines two confidential clients.

Synchronization client:

```text
Client ID: authorization-sync-service
Client authentication: On
Service accounts: Enabled
```

Grant its service account the narrow `realm-management` permissions needed to query users, groups,
and realm-role mappings. The bundled realm assigns only `view-users`. Its one-time initialization
job enforces that assignment on Keycloak's generated service-account user and enables full scope for
this local client so the assigned role is present in its client-credentials token.

Browser-login client:

```text
Client ID: employee-demo
Client authentication: On
Standard flow: Enabled
Valid redirect URI: http://localhost:8080/login/oauth2/code/keycloak
Valid post logout redirect URI: http://localhost:8080/demo-ui/signed-out
```

Keeping login and synchronization clients separate prevents the browser-login client from receiving
administrative service-account privileges.

The browser client requests only `openid` and uses explicit client scopes/mappers to minimize its
access token. It retains Keycloak lifecycle/session claims, `sub`, `acr`, and
`preferred_username`, while profile PII, LDAP employee attributes, groups, roles, and
`allowed-origins` are omitted. Profile information remains available through the ID token/UserInfo;
external groups and realm roles are retrieved independently by the synchronization client and
mapped to local assignments.

### Demo permissions

The seed defines two opaque UI resource identifiers:

```text
employeeDirectory
managerWorkspace
```

`EMPLOYEE_READ_ACCESS` contains `UI:EMPLOYEE_DIRECTORY` (pattern `employeeDirectory`).
`EMPLOYEE_MANAGEMENT_ACCESS` contains `UI:MANAGER_WORKSPACE` (pattern `managerWorkspace`). Existing
Keycloak group/realm-role mappings therefore produce this browser behavior:

```text
emma -> employee directory only
michael -> employee directory and manager workspace
```

The imported realm provides `emma` in `/authorization-demo/hr-analysts`, `michael` in
`/authorization-demo/hr-managers` with inherited realm role `people-manager`, and
`olivia` in `/authorization-demo/authorization-administrators`. Their password is
`demo`. Login-time synchronization converts those external authorities into local seeded roles;
Keycloak does not define application permissions directly.

The landing page hides unavailable links, and each page checks its UI permission again on direct
navigation. The containing `/demo-ui/**` URL resource remains `AUTHENTICATED`, demonstrating that
URL access and UI-component authorization are independent concerns. The controller injects core's
provider-neutral `AuthorizationService`; the demo contains no Keycloak-specific authorization
adapter.

### Run

Build the demo and start the bundled Keycloak, PostgreSQL, and LDAP services:

```bash
./mvnw -pl examples/authorization-demo -am package
docker compose -f examples/keycloak-employee-demo/docker-compose.yml up -d
```

Then run the packaged application (the `keycloak-demo` profile is active by default):

```bash
java -jar examples/authorization-demo/target/authorization-demo-0.1.0-SNAPSHOT.jar
```

The checked-in credentials are deliberately local-demo values. Defaults expect Keycloak at
`http://localhost:8081`, realm `employee-demo`, synchronization client
`authorization-sync-service`, browser client `employee-demo`, and issuer
`http://localhost:8081/realms/employee-demo`. Override these with
`AUTHORIZATION_KEYCLOAK_BASE_URL`, `AUTHORIZATION_KEYCLOAK_REALM`,
`AUTHORIZATION_KEYCLOAK_CLIENT_ID`, `AUTHORIZATION_KEYCLOAK_LOGIN_CLIENT_ID`, and
`AUTHORIZATION_KEYCLOAK_ISSUER_URI`; the corresponding client-secret variables are documented in
`examples/authorization-demo/demo.env`.

Open:

```text
http://localhost:8080/demo-ui/
```

An unauthenticated HTML request redirects to Keycloak. Sign in as `emma`, `michael`, or
`olivia` with password `demo`. After login, the success handler synchronizes that exact
`(issuer, subject)` before placing tokens in scoped HttpOnly cookies and redirecting to the demo
landing page. No curl/bootstrap sync is required. A synchronization failure returns HTTP 503
instead of treating the user as having no permissions.

The Sign out action submits a CSRF-protected POST to the framework logout endpoint. It clears the
local cookies, optionally revokes the refresh token, performs OIDC RP-initiated logout, and returns
to the public `/demo-ui/signed-out` page after ending the Keycloak SSO session.

Bearer-token curl or Postman calls remain supported for API testing, but are not required for the
sample pages. Query-parameter and runtime header authentication are not available.

## Nuxt/Nitro companion demo

The separate `examples/authorization-nuxt-demo` application reuses this realm and the same
`emma`, `michael`, and `olivia` identities. It runs Spring on port 8082 and
exposes the browser application, OAuth2 callbacks, and optional administration UI through Nuxt on
port 3000.
See its [README](../examples/authorization-nuxt-demo/README.md) for startup and behavior details.
