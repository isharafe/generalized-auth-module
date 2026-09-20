# Spring + Nuxt Authorization Demo

This non-published example pairs a Spring Boot backend with a Nuxt/Nitro frontend. It demonstrates
permission-aware UI, protected routes and requests, CSRF handling, refresh recovery, OIDC logout,
and the optional administration UI. Spring Security remains the enforcement boundary.

## Services

| Service | URL | Purpose |
| --- | --- | --- |
| Nuxt/Nitro | <http://localhost:3000> | Browser application and same-origin proxy, including the admin UI |
| Spring Boot | <http://localhost:8082> | Private backend for API, authorization, OAuth2, and admin resources |
| Keycloak | <http://localhost:8081> | Authentication and external authorities |

Always use port 3000 in the browser, including
<http://localhost:3000/authorization-admin/> for the admin UI. Port 8082 is exposed for backend
diagnostics, not as the browser entry point. OAuth tokens remain in Spring-managed HttpOnly cookies
and are never exposed to Nuxt application code.

## Quick start with containers

From the repository root:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml \
  --profile authorization-nuxt-demo up -d --build
```

Open <http://localhost:3000/>. Check startup with:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml logs -f keycloak-init
docker compose -f examples/keycloak-employee-demo/docker-compose.yml logs -f authorization-nuxt-backend
docker compose -f examples/keycloak-employee-demo/docker-compose.yml logs -f authorization-nuxt-frontend
```

On native Linux, `host.docker.internal` may need to map to `127.0.0.1` in the host's
`/etc/hosts`. Docker Desktop resolves it automatically.

## Run from source

Requirements: Java 21, Docker Compose, and Node.js 22.19 or later.

In one terminal:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml up -d
docker compose -f examples/keycloak-employee-demo/docker-compose.yml logs -f keycloak-init
./mvnw -pl examples/authorization-nuxt-demo -am package
java -jar examples/authorization-nuxt-demo/target/authorization-nuxt-demo-0.1.0-SNAPSHOT.jar
```

Wait for `LDAP federation demo is ready.`, then run the frontend in another terminal:

```bash
cd examples/authorization-nuxt-demo/frontend
npm ci --legacy-peer-deps
npm run dev
```

Open <http://localhost:3000/> and sign in. All passwords are `demo`.

| User | Demonstrates |
| --- | --- |
| `emma` | Visible employee directory, disabled edit controls, read-only backend access |
| `michael` | Directory and manager workspace, enabled edits, read/write backend access |
| `olivia` | Proxied authorization admin UI; no employee access |

After signing in as `olivia`, open <http://localhost:3000/authorization-admin/>.

## How it works

The seed at `src/main/resources/authorization/nuxt-demo-seed.yml` defines separate presentation
and enforcement permissions:

- `UI:EMPLOYEE_DIRECTORY`, `UI:MANAGER_WORKSPACE`, and `UI:EMPLOYEE_EDIT` control Nuxt content.
- `URL:EMPLOYEE_VIEW` and `URL:EMPLOYEE_EDIT` protect Spring endpoints.
- `AUTHZ_SYSTEM_ADMIN` protects the administration UI and API.

Nitro proxies OAuth callbacks, security endpoints, application API requests, and
`/authorization-admin/**` to Spring. Unsafe requests obtain a CSRF token. Concurrent 401 responses
share one refresh operation, retry once, and reload the current-user permission snapshot. The
snapshot contains both the demo's `UI:*` presentation permissions and its `URL:*` backend
permissions. UI checks fail closed, but they never replace backend authorization.

See the [Nuxt integration guide](../../docs/23-nuxt-integration.md) for configuration and lifecycle
details, or the [integration package README](../../integrations/authorization-nuxt/README.md) for
usage examples.

## Configuration and reset

Spring defaults are checked in. `demo.env` lists backend Keycloak overrides, and
`frontend/.env.example` documents:

| Variable | Default |
| --- | --- |
| `AUTHORIZATION_BACKEND_URL` | `http://localhost:8082` |
| `NUXT_PUBLIC_BASE_URL` | `http://localhost:3000` |

Changing the public origin also requires matching Keycloak login and logout redirect URIs.

After editing the checked-in Keycloak realm, recreate the identity data:

```bash
examples/keycloak-employee-demo/reset-demo-data.sh
docker compose -f examples/keycloak-employee-demo/docker-compose.yml up -d
```

## Verify

The Maven reactor does not run this example's npm checks:

```bash
./mvnw -pl examples/authorization-nuxt-demo -am test

cd examples/authorization-nuxt-demo/frontend
npm ci --legacy-peer-deps
npm test
npm run typecheck
npm run build
```
