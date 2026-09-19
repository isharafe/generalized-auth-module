# Authorization Demo

This non-published Spring Boot application demonstrates Keycloak login with application-owned
authorization. Keycloak identifies users and supplies external authorities; the framework maps
those authorities to local roles and permission groups, then authorizes from its local H2 database.

All credentials in this guide are intentionally weak and are for local use only.

## Quick start

Requirements: Java 21 and Docker Compose.

From the repository root:

```bash
./mvnw -pl examples/authorization-demo -am package
docker compose -f examples/keycloak-employee-demo/docker-compose.yml up -d
```

Watch the one-time identity setup:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml logs -f keycloak-init
```

Continue when the log contains:

```text
Authorization synchronization Admin API access verified.
LDAP federation demo is ready.
```

Start Spring in another terminal:

```bash
java -jar examples/authorization-demo/target/authorization-demo-0.1.0-SNAPSHOT.jar
```

Open:

- Demo UI: <http://localhost:8080/demo-ui/>
- Authorization admin UI: <http://localhost:8080/authorization-admin/>
- Keycloak admin console: <http://localhost:8081/admin/>

The `keycloak-demo` profile is active by default. Use `localhost:8080` or `127.0.0.1:8080`;
other origins require matching Keycloak callback URLs.

### Run everything in containers

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml \
  --profile authorization-demo up -d --build
```

Follow application logs with:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml logs -f authorization-demo
```

On native Linux, `host.docker.internal` may need to map to `127.0.0.1` in the host's
`/etc/hosts`. Docker Desktop resolves it automatically.

## Demo users

All application users have password `demo`.

| User | Effective application access |
| --- | --- |
| `emma` | Profile, employee list, and employee directory |
| `michael` | Profile, employee view/edit, directory, and manager workspace |
| `olivia` | Profile and authorization admin UI/API; no employee access |

Expected results:

| Request or page | No login | `emma` | `michael` | `olivia` |
| --- | ---: | ---: | ---: | ---: |
| `GET /demo/public` | 200 | 200 | 200 | 200 |
| `GET /demo/profile` | 401 | 200 | 200 | 200 |
| `GET /demo/employees` | 401 | 200 | 200 | 403 |
| `PUT /demo/employees/1` | 401 | 403 | 200 | 403 |
| `/authorization-admin/` | Login | 403 | 403 | Allowed |

HTML requests may redirect to Keycloak instead of showing a literal 401 response.

The bundled OpenLDAP directory also provides `alice`, `bob`, `john`, `mary`, `susan`, and
`david` with password `demo`. `alice` and `bob` inherit the mapped `people-manager` realm role;
the others demonstrate authenticated identities without employee permissions.

## How authorization is assigned

The seed at
`src/main/resources/authorization/demo-seed.yml` maps external authorities to local roles:

```text
/authorization-demo/hr-analysts                  -> HR_ANALYST
people-manager                                   -> HR_MANAGER
/authorization-demo/authorization-administrators -> AUTHORIZATION_ADMINISTRATOR
```

Those roles compose local permission groups:

```text
HR_ANALYST -> EMPLOYEE_READ_ACCESS
  URL:EMPLOYEE_VIEW       GET:/demo/employees/**
  UI:EMPLOYEE_DIRECTORY   employeeDirectory

HR_MANAGER -> EMPLOYEE_READ_ACCESS + EMPLOYEE_MANAGEMENT_ACCESS
  URL:EMPLOYEE_EDIT       PUT:/demo/employees/**
  UI:MANAGER_WORKSPACE    managerWorkspace
```

UI permissions control presentation; URL permissions enforced by Spring Security remain the
security boundary. A missing resource rule denies access by default.

## Try changing access

- Edit `demo-seed.yml` to change mappings or role/group membership, then restart the application.
- Change a user's groups or realm roles in the Keycloak admin console, then sign out and back in.
- Sign in as `olivia` and add a `MANUAL` assignment in the authorization admin UI.

Login performs targeted synchronization. It may replace only `IDENTITY_SYNC` assignments; seeded
and manual assignments are preserved. Because the demo uses in-memory H2, local changes disappear
when the application restarts.

For complete seed semantics, resource-rule examples, and Keycloak configuration, see:

- [Seed framework](../../docs/05-seeding.md)
- [Runnable demo architecture](../../docs/14-demo-application.md)
- [Keycloak integration](../../docs/09-keycloak-integration.md)
- [Complete configuration reference](../../docs/11-configuration.md)

## Configuration

`src/main/resources/application.yml` selects the default profile and H2 database.
`src/main/resources/application-keycloak-demo.yml` configures OAuth2 login, cookies, CSRF, logout,
and synchronization. `demo.env` lists supported environment overrides and their local defaults.

The browser-login and synchronization clients are deliberately separate. Tokens stay in HttpOnly
cookies, and the service account has only the Keycloak management access needed to read users.
Scheduled synchronization is disabled in this demo; login and admin actions trigger it explicitly.

Production deployments must use HTTPS, secure cookies, durable storage, managed secrets,
least-privilege clients, and restricted redirect URIs.

## Reset and stop

Restarting only Spring resets the in-memory authorization database. To reset all demo identity data:

```bash
examples/keycloak-employee-demo/reset-demo-data.sh
docker compose -f examples/keycloak-employee-demo/docker-compose.yml up -d
```

This removes the demo's containers and three data volumes. To stop without deleting data:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml down
```

See the [Keycloak/OpenLDAP demo](../keycloak-employee-demo/README.md) for directory users,
federation, and identity-service troubleshooting.
