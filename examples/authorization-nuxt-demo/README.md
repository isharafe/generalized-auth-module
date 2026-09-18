# Spring + Nuxt authorization demo

This non-published example pairs a Spring Boot backend with a Nuxt/Nitro frontend. It demonstrates
UI permission snapshots, permission-aware components/directives/routes, authorized API requests,
CSRF handling, refresh-token recovery, OIDC logout, and the optional packaged administration UI.
Spring remains the enforcement boundary for every backend request.

## What runs where

| Process | URL | Purpose |
| --- | --- | --- |
| Nuxt/Nitro | `http://localhost:3000` | Browser-facing application and same-origin proxy |
| Spring Boot | `http://localhost:8082` | API, authorization engine, OAuth2, and admin UI |
| Keycloak | `http://localhost:8081` | OIDC authentication and external authorities |

Always open the demo through port `3000`. Nitro proxies OAuth2 callbacks, framework security
endpoints, application API requests, and `/authorization-admin/**` to Spring. Access and refresh
tokens stay in Spring-managed HttpOnly cookies and are never exposed to Nuxt application code.

## Run the demo

Requirements are Java 21 or later, Docker Compose, and Node.js 22.19 or later.

From the repository root, start a fresh Keycloak environment and build the backend:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml up -d
docker compose -f examples/keycloak-employee-demo/docker-compose.yml logs -f keycloak-init
./mvnw -pl examples/authorization-nuxt-demo -am package
java -jar examples/authorization-nuxt-demo/target/authorization-nuxt-demo-0.1.0-SNAPSHOT.jar
```

Wait for `LDAP federation demo is ready.` in the initialization log, then stop following the log
with Ctrl+C; the containers remain running.

In another terminal, install and run the frontend:

```bash
cd examples/authorization-nuxt-demo/frontend
npm ci --legacy-peer-deps
npm run dev
```

Open `http://localhost:3000`, choose **Sign in**, and use one of the identities below. All demo
passwords are `demo`.

| User | UI behavior | Backend behavior |
| --- | --- | --- |
| `emma` | Employee directory is visible; edit inputs are disabled | Can view employees; cannot edit or administer |
| `michael` | Directory and manager workspace are visible; edit inputs are enabled | Can view and edit employees; cannot administer |
| `olivia` | Administration card/link is visible | Can use `/authorization-admin/`; has no employee access |

The admin UI is available, after signing in as `olivia`, at
`http://localhost:3000/authorization-admin/`. Both its static assets and REST calls remain on the
Nuxt origin and are proxied to the `authorization-admin` dependency in Spring.

## How the behavior is configured

The application-owned authorization model is in
`src/main/resources/authorization/nuxt-demo-seed.yml`:

- `UI:EMPLOYEE_DIRECTORY` controls the directory card, navigation link, and route.
- `UI:MANAGER_WORKSPACE` controls the manager card, navigation link, and route.
- `UI:EMPLOYEE_EDIT` enables or disables employee inputs and buttons.
- `UI:AUTHORIZATION_ADMIN` shows the administration navigation.
- `URL:EMPLOYEE_VIEW` and `URL:EMPLOYEE_EDIT` independently protect Spring endpoints.
- `AUTHZ_SYSTEM_ADMIN`, supplied by `authorization-admin`, protects the management UI and API.

The local `HR_ANALYST` and `HR_MANAGER` roles compose `EMPLOYEE_READ_ACCESS` and
`EMPLOYEE_MANAGEMENT_ACCESS` permission groups. The analyst is mapped through the Keycloak
`/authorization-demo/hr-analysts` group, while the manager is mapped through the inherited
`people-manager` realm role. This intentionally demonstrates both supported external-authority
mapping types without naming permissions as roles.

The seed maps the existing Keycloak groups and realm roles to local roles or permission groups.
Keycloak does not define application URL or UI permissions. To try different behavior, change group
memberships in Keycloak or adjust the external-authority mappings and role/group membership in the
seed, then restart Spring with a fresh in-memory H2 database.

Keep presentation and enforcement grants paired when appropriate. For example, enabling
`UI:EMPLOYEE_EDIT` makes the control usable, while `URL:EMPLOYEE_EDIT` is what actually permits the
`PUT /demo/employees/{id}` request. Omitting the URL permission still produces HTTP 403 even if the
button is visible.

The Keycloak realm import is maintained in
`../keycloak-employee-demo/keycloak/employee-demo-realm.json`. It registers both the original
Spring-only callback on port 8080 and this demo's public callback on port 3000. When realm import
data changes, recreate the demo Keycloak containers and volumes before starting them again:

```bash
examples/keycloak-employee-demo/reset-demo-data.sh
docker compose -f examples/keycloak-employee-demo/docker-compose.yml up -d
```

## Runtime configuration

Spring defaults are usable as checked in. `demo.env` lists the available Keycloak overrides. The
frontend reads these optional variables (see `frontend/.env.example`):

| Variable | Default | Meaning |
| --- | --- | --- |
| `AUTHORIZATION_BACKEND_URL` | `http://localhost:8082` | Private Nitro-to-Spring origin |
| `NUXT_PUBLIC_BASE_URL` | `http://localhost:3000` | Browser-visible origin used for OAuth redirects |

If the public host or port changes, update `NUXT_PUBLIC_BASE_URL` and add the matching login callback
and post-logout URI to the Keycloak client. Spring uses trusted forwarded headers from Nitro to
construct those redirects.

The Nuxt module configuration in `frontend/nuxt.config.ts` includes
`backendProxyPrefixes: ['/authorization-admin']`. This is required because the admin UI uses its
own root-relative asset and API paths rather than the module's application-API proxy prefix.

## Request lifecycle

On login, Spring performs targeted Keycloak synchronization before setting its cookies. Nuxt then
loads `/authorization/ui/permissions`, which returns only the current user's enabled `UI:*` codes.
The component, directive, composable, and route middleware fail closed until that snapshot is
ready.

Unsafe calls lazily obtain a CSRF token and send the server-selected header. If an application
request receives HTTP 401, the integration serializes a refresh request, retries the original call
once, and reloads the UI permission snapshot. Logout is also CSRF-protected and ends both the local
cookie session and Keycloak SSO session.

## Verify independently

The Maven reactor intentionally does not run npm. Verify each side explicitly:

```bash
./mvnw -pl examples/authorization-nuxt-demo -am test

cd examples/authorization-nuxt-demo/frontend
npm ci --legacy-peer-deps
npm test
npm run typecheck
npm run build
```
