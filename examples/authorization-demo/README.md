# Authorization Demo

This application demonstrates browser authentication with Keycloak and application-owned
authorization with the reusable authorization framework. Keycloak establishes who the user is and
supplies external groups and realm roles. The application maps selected external authorities to
local roles and permission groups, then makes authorization decisions from its local H2 database.

All credentials in this guide are deliberately weak local-demo credentials. Do not reuse them in a
real environment.

## Quick start

From the repository root, build the application and start the bundled PostgreSQL, OpenLDAP, and
Keycloak services:

```bash
./mvnw -pl examples/authorization-demo -am package
docker compose -f examples/keycloak-employee-demo/docker-compose.yml up -d
```

Wait for the one-time Keycloak authorization-client verification and LDAP synchronization to
complete:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml logs -f keycloak-init
```

It is ready when the log contains both `Authorization synchronization Admin API access verified.`
and `LDAP federation demo is ready.`. The initialization job exits instead of printing these
messages if the service account would return 403 to an endpoint used during application login.
Start the application in a second terminal:

```bash
java -jar examples/authorization-demo/target/authorization-demo-0.1.0-SNAPSHOT.jar
```

The `keycloak-demo` Spring profile is active by default, so no environment variables are required
for this local setup. Open:

- Demo UI: <http://localhost:8080/demo-ui/>
- Authorization admin UI: <http://localhost:8080/authorization-admin/>
- Keycloak admin console: <http://localhost:8081/admin/>

The imported `employee-demo` browser client already allows the exact Spring Security callbacks for
both `localhost:8080` and `127.0.0.1:8080`, including the post-logout redirect. Use one of those two
application origins unless you also register the new origin's callback URLs in Keycloak.

An unauthenticated browser request to a protected HTML page redirects to Keycloak. After a
successful login, the application synchronizes that user from Keycloak, stores the resulting local
assignments, and redirects to the demo UI.

## Application login users

The `employee-demo` realm in `../keycloak-employee-demo` supplies the following purpose-built users.
Their password is `demo`.

| Username | Effective application access |
|---|---|
| `emma` | Profile, employee list, and employee directory |
| `michael` | Profile, employee update, employee directory, and manager workspace |
| `olivia` | Profile and authorization admin UI/API; no employee access |

The resulting behavior is:

| Request or page | No login | `emma` | `michael` | `olivia` |
|---|---:|---:|---:|---:|
| `GET /demo/public` | 200 | 200 | 200 | 200 |
| `GET /demo/profile` | 401 | 200 | 200 | 200 |
| `GET /demo/employees` | 401 | 200 | 200 | 403 |
| `PUT /demo/employees/1` | 401 | 403 | 200 | 403 |
| `/demo-ui/` landing page | Login | Employee directory | Directory and manager workspace | No workspaces |
| `/authorization-admin/` | Login | 403 | 403 | Allowed |

For HTML requests, an unauthenticated response may be represented by a redirect to Keycloak rather
than a literal 401 page. Direct navigation to `/demo-ui/employee-directory` and
`/demo-ui/manager-workspace` is checked on the server; hiding a link on the landing page is not the
security boundary.

### Why each user behaves differently

The realm gives the users these external authorities:

```text
emma
  group /authorization-demo/hr-analysts

michael
  group /authorization-demo/hr-managers
  inherited realm role people-manager

olivia
  group /authorization-demo/authorization-administrators
```

`src/main/resources/authorization/demo-seed.yml` maps those authorities to application-owned
authorization objects:

```text
/authorization-demo/hr-analysts                  -> HR_ANALYST
people-manager                                   -> HR_MANAGER
/authorization-demo/authorization-administrators -> AUTHORIZATION_ADMINISTRATOR
```

The analyst demonstrates a Keycloak group mapping. The manager group grants `people-manager`, so
the manager demonstrates a Keycloak realm-role mapping. Local roles compose capability-oriented
permission groups:

```text
HR_ANALYST
  EMPLOYEE_READ_ACCESS
    URL:EMPLOYEE_VIEW       GET:/demo/employees/**
    UI:EMPLOYEE_DIRECTORY   employeeDirectory

HR_MANAGER
  EMPLOYEE_READ_ACCESS
  EMPLOYEE_MANAGEMENT_ACCESS
    URL:EMPLOYEE_EDIT       PUT:/demo/employees/**
    UI:MANAGER_WORKSPACE    managerWorkspace
```

The admin module contributes the `AUTHZ_SYSTEM_ADMIN` permission group but assigns no user. The
demo's `AUTHORIZATION_ADMINISTRATOR` role contains that group.

## LDAP-backed employee users

The same Keycloak demo federates users from OpenLDAP. After `keycloak-init` has completed, all of
these users can log in to this application with password `demo`:

| Username | Job/function | LDAP-derived Keycloak group | Effective application access |
|---|---|---|---|
| `alice` | Department head | `/Function-Department-Heads` | HR manager |
| `bob` | Manager | `/Function-Managers` | HR manager |
| `john` | Employee | `/Function-Employees` | Authentication only |
| `mary` | Employee | `/Function-Employees` | Authentication only |
| `susan` | HR administrator | `/Function-HR-Admins` | Authentication only |
| `david` | Employee | `/Function-Employees` | Authentication only |

Alice and Bob inherit the `people-manager` role, which the shipped seed maps to local `HR_MANAGER`.
The remaining LDAP identities can open `/demo/profile`, but employee operations and the
authorization admin UI return 403.

LDAP groups produce business roles such as `employee`, `people-manager`, and `hr-administrator`,
but the application seed maps only `people-manager` by default.
Keycloak roles never become URL or UI permissions implicitly. This demonstrates the boundary
between external identity data and local application authorization.

## Changing behavior

There are three useful ways to change access in this demo.

### 1. Map another Keycloak group or role in the seed

Edit `src/main/resources/authorization/demo-seed.yml`. For example, these mappings make regular
LDAP employees HR analysts and Susan an authorization administrator. Bob and Alice already receive
`HR_MANAGER` through the shipped `people-manager` role mapping.

```yaml
authorization:
  seed:
    external-authority-mappings:
      # Keep the existing mappings, then append these entries.
      - source-system: KEYCLOAK
        authority-type: GROUP
        authority: /Function-Employees
        target:
          type: ROLE
          code: HR_ANALYST

      - source-system: KEYCLOAK
        authority-type: GROUP
        authority: /Function-HR-Admins
        target:
          type: ROLE
          code: AUTHORIZATION_ADMINISTRATOR
```

External authority values are exact and Keycloak group paths include the leading `/`. Keep the
existing `external-authority-mappings` entries when adding these; a second YAML key with the same
name would replace the first key during parsing.

Restart the application to reapply the seed, then sign out and sign back in as the affected user so
login-time targeted synchronization recalculates `IDENTITY_SYNC` assignments. Because the demo uses
an in-memory H2 database, every application restart begins with a fresh local authorization store.

Realm roles can be mapped in the same way by using `authority-type: ROLE`. The shipped
`authority: people-manager` mapping targets `HR_MANAGER`. Use whichever authority most accurately
represents the responsibility managed by the identity provider.

### 2. Change a user's Keycloak authorities

Open <http://localhost:8081/admin/>, sign in with Keycloak administrator credentials `admin` /
`admin`, and select the `employee-demo` realm. Under **Users**, change a user's **Groups** or
**Role mapping**. Sign the application user out and back in to trigger targeted synchronization.

Useful built-in groups are:

```text
/authorization-demo/hr-analysts
/authorization-demo/hr-managers
/authorization-demo/authorization-administrators
```

For example, adding `emma` to `/authorization-demo/hr-managers` grants the inherited
`people-manager` role and therefore the local `HR_MANAGER` role after the next synchronization.
Removing a mapped authority removes only the corresponding `IDENTITY_SYNC` assignment; manual and
seed-owned assignments are preserved.

If you edit `employee-demo-realm.json` instead of using the admin console, reset the Keycloak data
before starting it again because realm import does not overwrite an already imported realm:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml down -v
docker compose -f examples/keycloak-employee-demo/docker-compose.yml up -d
```

This deletes all three demo service volumes and recreates them from the checked-in realm and LDAP
fixtures.

### 3. Assign local access in the admin UI

First log in once as the target user so targeted synchronization creates the local user. Then sign
in as `olivia`, open <http://localhost:8080/authorization-admin/>, select the user,
and assign a local role or permission group such as `HR_ANALYST`, `HR_MANAGER`,
`EMPLOYEE_READ_ACCESS`, or `EMPLOYEE_MANAGEMENT_ACCESS`.

Admin-created assignments have source `MANUAL`. Later Keycloak synchronization does not delete
them. In this demo they last only for the life of the application process because H2 is in-memory.

## Changing permissions and resource rules

The seed file controls two separate layers:

- A URL `ResourceRule` determines whether a request is public, merely authenticated, authorized by
  permission, or always denied.
- A `Permission` is a key that can satisfy an `AUTHORIZED` rule.

Current rules include:

```text
*:/demo/public                    PERMIT_ALL
*:/demo/profile                   AUTHENTICATED
*:/demo/employees/**              AUTHORIZED
*:/demo-ui/**                     AUTHENTICATED
employeeDirectory                AUTHORIZED (UI resource)
managerWorkspace                 AUTHORIZED (UI resource)
*:/authorization-admin/api/**     AUTHORIZED
*:/authorization-admin/**         AUTHORIZED
```

Examples of configuration changes:

- Add `URL:EMPLOYEE_EDIT` to `EMPLOYEE_READ_ACCESS` to let `emma` update employees.
- Remove `UI:MANAGER_WORKSPACE` from `EMPLOYEE_MANAGEMENT_ACCESS` to hide and deny the manager
  workspace for `michael`.
- Change the `/demo/profile` rule from `AUTHENTICATED` to `AUTHORIZED` and define/grant a matching
  URL permission if a login alone should no longer be enough.
- Add a new type-qualified UI permission and UI resource rule to demonstrate another component.

Permission group and role membership in a seed is replacement-oriented: the listed members become
the stored membership for that object. Permission codes must remain type-qualified, such as
`URL:EMPLOYEE_VIEW` or `UI:EMPLOYEE_DIRECTORY`. URL permission patterns include the HTTP method,
while UI patterns such as `employeeDirectory` are exact opaque identifiers.

No matching resource rule is denied by default. For example, `/demo/public/` is different from the
seeded exact `/demo/public` route and is not automatically public.

## Configuration reference

The base configuration is in `src/main/resources/application.yml`. It selects the `keycloak-demo`
profile, creates an in-memory H2 datasource, and loads `authorization/demo-seed.yml`.

`src/main/resources/application-keycloak-demo.yml` configures OAuth2 login, JWT validation,
HttpOnly token cookies, CSRF, logout, and the Keycloak synchronization client. The checked-in
`demo.env` lists all supported environment overrides:

| Variable | Local default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `keycloak-demo` | Activates the runnable authentication profile |
| `AUTHORIZATION_KEYCLOAK_BASE_URL` | `http://localhost:8081` | Keycloak Admin API base URL |
| `AUTHORIZATION_KEYCLOAK_REALM` | `employee-demo` | Realm queried by the sync client |
| `AUTHORIZATION_KEYCLOAK_ISSUER_URI` | `http://localhost:8081/realms/employee-demo` | OIDC/JWT issuer and stable local issuer |
| `AUTHORIZATION_KEYCLOAK_CLIENT_ID` | `authorization-sync-service` | Service-account client for user/group/role reads |
| `AUTHORIZATION_KEYCLOAK_CLIENT_SECRET` | `authorization-sync-service-demo-secret` | Service-account secret |
| `AUTHORIZATION_KEYCLOAK_LOGIN_CLIENT_ID` | `employee-demo` | Browser OAuth2 client |
| `AUTHORIZATION_KEYCLOAK_LOGIN_CLIENT_SECRET` | `employee-demo-secret` | Browser OAuth2 client secret |

The defaults and `demo.env` contain the same local values. To load the file explicitly:

```bash
set -a
. examples/authorization-demo/demo.env
set +a
java -jar examples/authorization-demo/target/authorization-demo-0.1.0-SNAPSHOT.jar
```

The browser-login client and synchronization service client are intentionally separate. The browser
client performs authorization-code login; only the service account receives the Keycloak management
permission needed to read users. The imported realm assigns only `realm-management:view-users` to
that service account. The one-time `keycloak-init` job enforces that assignment on Keycloak's
generated service-account user and enables its effective token scope before reporting that the demo
is ready. This prevents Admin API 403 responses during login-time synchronization.

The configured full and incremental synchronization cron expressions are `-`, so scheduled sync is
disabled. Browser login performs targeted synchronization. Full, incremental, and targeted actions
are also available through the admin component to an authorized administrator.

The demo sets cookie and CSRF `secure` flags to `false` for local HTTP. Set them to `true` when using
HTTPS. Real deployments must also replace the demo secrets, restrict redirect URIs, use durable
storage, and configure production-grade Keycloak and database settings.

## Reset and stop

Restarting only the Java application resets its in-memory H2 authorization data. To reset Keycloak,
PostgreSQL, and LDAP to the checked-in fixtures as well:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml down -v
docker compose -f examples/keycloak-employee-demo/docker-compose.yml up -d
```

To stop the services without deleting their data:

```bash
docker compose -f examples/keycloak-employee-demo/docker-compose.yml down
```

For details about the employee hierarchy, LDAP attributes, and federation setup, see
[`../keycloak-employee-demo/README.md`](../keycloak-employee-demo/README.md).
