# Keycloak + OpenLDAP Employee Hierarchy Demo

This demo contains:

- PostgreSQL for Keycloak
- OpenLDAP with sample employee and group data
- Keycloak configured with LDAP User Federation
- LDAP attribute mappers
- one LDAP group mapper
- automatic initial LDAP group/user synchronization
- Keycloak roles and Authorization Services policies from the earlier demo

## Start

```bash
docker compose up -d
```

This command starts only the identity infrastructure. To build and start the Spring-only demo as
well, enable its Compose profile:

```bash
docker compose --profile authorization-demo up -d --build
```

To build and start the separate Spring backend and Nitro frontend for the Nuxt demo instead:

```bash
docker compose --profile authorization-nuxt-demo up -d --build
```

The Spring demo is then available at <http://localhost:8080/demo-ui/>. The Nuxt demo is available at
<http://localhost:3000/> and its Spring backend remains exposed at <http://localhost:8082/> for
diagnostics.

Containerized applications use `host.docker.internal:8081` as the browser-visible Keycloak issuer.
Docker Desktop supplies that hostname. On native Linux, if it does not already resolve on the host,
add this entry to `/etc/hosts` before signing in:

```text
127.0.0.1 host.docker.internal
```

Compose separately maps the same hostname to the Docker host gateway inside the Spring containers.

Watch the one-time federation bootstrap:

```bash
docker compose logs -f keycloak-init
```

It should finish with:

```text
Authorization synchronization Admin API access verified.
LDAP federation demo is ready.
```

Open Keycloak (Docker Compose exposes the container's port `8080` as host port `8081`):

- http://localhost:8081/admin/

Admin login:

- username: `admin`
- password: `admin`

Select realm:

- `employee-demo`

The realm's `employee-demo` OIDC client accepts callbacks for both runnable applications:

- Spring-only demo: `http://localhost:8080/login/oauth2/code/keycloak`
- Nuxt/Nitro demo: `http://localhost:3000/login/oauth2/code/keycloak`

The corresponding post-logout pages are registered as well. The Nuxt callback is proxied to its
Spring backend, so browser navigation remains on port 3000.

The realm also includes three local application-demo identities, all with password `demo`:

| User | Keycloak authority used by the authorization demos |
|---|---|
| `emma` | Group `/authorization-demo/hr-analysts` |
| `michael` | Group `/authorization-demo/hr-managers`, which grants `people-manager` |
| `olivia` | Group `/authorization-demo/authorization-administrators` |

The demo seeds map the analyst group, the manager's business role, and the administrator group to
application-owned roles. This intentionally demonstrates both group and realm-role mappings.

## LDAP

LDAP is available to other containers at:

```text
ldap://ldap:389
```

and from your host at:

```text
ldap://localhost:1389
```

LDAP administrator:

```text
DN:       cn=admin,dc=demo,dc=local
Password: admin
Base DN:  dc=demo,dc=local
```

To inspect LDAP directly:

```bash
docker exec keycloak-demo-ldap   ldapsearch -x   -H ldap://localhost:389   -D "cn=admin,dc=demo,dc=local"   -w admin   -b "dc=demo,dc=local"
```

## Sample employees

Every employee's LDAP password is `demo`.

| User  | Function        | Department | Location | Manager |
|-------|-----------------|------------|----------|---------|
| alice | Department Head | PAYMENTS   | SG       | -       |
| bob   | Manager         | PAYMENTS   | SG       | Alice   |
| john  | Employee        | PAYMENTS   | SG       | Bob     |
| mary  | Employee        | PAYMENTS   | LONDON   | Bob     |
| susan | HR Admin        | HR         | SG       | -       |
| david | Employee        | FINANCE    | TOKYO    | -       |

The reporting relationship is stored using the standard LDAP `manager`
attribute. Example:

```text
uid=john,...
manager: uid=bob,ou=People,dc=demo,dc=local
```

## LDAP -> Keycloak attribute mapping

The federation provider is already configured under:

```text
User federation -> demo-openldap
```

Mappings:

| LDAP attribute     | Keycloak user field/attribute |
|--------------------|-------------------------------|
| uid                | username                      |
| givenName          | firstName                     |
| sn                 | lastName                      |
| mail               | email                         |
| employeeNumber     | employeeId                    |
| departmentNumber   | department                    |
| l                  | location                      |
| manager            | managerDn                     |
| title              | jobTitle                      |

Keycloak is configured in `READ_ONLY` mode and imports users locally.
Passwords are not copied into Keycloak; authentication is validated against
LDAP.

## LDAP groups

All LDAP groups are below:

```text
ou=Groups,dc=demo,dc=local
```

Examples:

```text
Department-Payments
Location-Singapore
Function-Managers
Function-HR-Admins
Team-Bob
```

They map to top-level Keycloak groups with the same names:

```text
/Department-Payments
/Location-Singapore
/Function-Managers
/Team-Bob
```

The prefixes keep department, location, function and team membership visually
separate while allowing the demo to use a single LDAP group mapper.

The functional Keycloak groups carry roles:

```text
Function-Employees
  -> employee

Function-Managers
  -> employee, people-manager, request-approver

Function-Department-Heads
  -> employee, people-manager, department-head, request-approver

Function-HR-Admins
  -> employee, hr-administrator, request-approver
```

These are business-responsibility roles rather than individual permissions. Therefore Bob becomes
an employee, people manager, and request approver because LDAP says Bob belongs to
`Function-Managers`; application permissions are still defined by each application.

## Why one LDAP group mapper?

For this demo all company groups are placed under one LDAP branch and handled
by one Keycloak group mapper. This is simpler and avoids current Keycloak 26.x
edge cases around multiple LDAP group mappers.

For a production AD, you can still scope the single mapper with LDAP filters,
or test multiple mappers against the exact Keycloak version you deploy.

## Automatic configuration and sync

Docker Compose only starts/orchestrates the containers. The Keycloak federation
definition and LDAP mappers are declarative in:

```text
keycloak/employee-demo-realm.json
```

The initial synchronization is executed by:

```text
keycloak/sync-ldap.sh
```

inside the one-time `keycloak-init` service.

The script uses Keycloak's bundled `kcadm.sh` to:

1. wait for Keycloak,
2. enforce the authorization sync service account's read-only `view-users` grant,
3. authenticate as that service account and verify the user, group-membership, and realm-role Admin
   API endpoints used by the authorization demo,
4. test LDAP authentication,
5. synchronize LDAP groups,
6. synchronize all LDAP users,
7. refresh group memberships.

You can rerun the same automatic group + user synchronization after
changing LDAP data with:

```bash
docker compose run --rm keycloak-init
```

You can also trigger synchronization manually from:

```text
User federation
  -> demo-openldap
  -> Synchronize all users
```

and from the LDAP group mapper UI.

## Verify a federated user

In Keycloak go to:

```text
Users -> bob
```

You should see LDAP-backed attributes such as:

```text
employeeId = 1002
department = PAYMENTS
location   = SG
managerDn  = uid=alice,ou=People,dc=demo,dc=local
jobTitle   = Payments Manager
```

and group memberships including:

```text
/Department-Payments
/Location-Singapore
/Function-Managers
/Team-Bob
/Team-Alice-Department
```

Because `Function-Managers` has Keycloak role mappings, Bob inherits
`employee`, `people-manager`, and `request-approver`.

## Obtain a minimized JWT using an LDAP password

```bash
curl -s   -X POST http://localhost:8081/realms/employee-demo/protocol/openid-connect/token   -H "Content-Type: application/x-www-form-urlencoded"   -d "client_id=employee-demo"   -d "client_secret=employee-demo-secret"   -d "grant_type=password"   -d "scope=openid"   -d "username=bob"   -d "password=demo"
```

The browser client deliberately keeps its access token small. In addition to Keycloak's signed
token lifecycle and session fields, it contains the stable subject, authentication context, and a
display username:

```json
{
  "iss": "http://localhost:8081/realms/employee-demo",
  "sub": "<stable-keycloak-user-id>",
  "typ": "Bearer",
  "azp": "employee-demo",
  "acr": "1",
  "scope": "openid",
  "preferred_username": "bob"
}
```

Names, email, LDAP employee attributes, groups, roles, and allowed origins are excluded from access
tokens. Profile and LDAP attributes remain available in the ID token and UserInfo response. The
authorization component retrieves groups and realm roles through its separate, least-privilege
Admin API client and maps them to local assignments; normal API requests never authorize from
token group or role claims.

## Demo change: prove synchronization

For example, edit `ldap/bootstrap.ldif` before the first startup, or modify LDAP
with `ldapmodify`, then run a Keycloak synchronization.

A useful live demo is to move Bob from `Function-Managers` to
`Function-Employees` in LDAP and sync again. His effective Keycloak roles then
change because his LDAP group membership changed.

## Reset everything

OpenLDAP bootstrap LDIF is processed only when its data volume is empty, and
Keycloak startup import skips an already-existing realm. This also means changes to client scopes
or token mappers in the checked-in realm file do not affect an existing demo realm. To return to
the current sample data:

```bash
./reset-demo-data.sh
docker compose up -d
```

The reset script can be run from any working directory. It removes only this Compose project's
containers (including either application profile), network, and three persistent volumes; it does
not remove downloaded Docker images or files in this directory.

## Security note

This project deliberately uses:

- HTTP LDAP rather than LDAPS,
- trivial passwords,
- an LDAP admin bind account,
- Keycloak `start-dev`,
- a client secret committed in the demo config.

Those choices are for a local demonstration only. A real client deployment
should use LDAPS/StartTLS, a least-privilege LDAP bind/service account,
production Keycloak settings, secrets management, and the client's actual AD
schema.
