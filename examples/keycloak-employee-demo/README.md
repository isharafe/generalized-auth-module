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
  -> VIEW_SELF, EDIT_SELF

Function-Managers
  -> VIEW_SELF, EDIT_SELF, VIEW_TEAM, EDIT_TEAM, APPROVE

Function-Department-Heads
  -> VIEW_SELF, EDIT_SELF, VIEW_TEAM, EDIT_TEAM,
     VIEW_DEPARTMENT, APPROVE

Function-HR-Admins
  -> VIEW_SELF, EDIT_SELF, VIEW_ALL, EDIT_ALL, APPROVE
```

Therefore Bob gets manager permissions because LDAP says Bob belongs to
`Function-Managers`; the permissions are not assigned directly to Bob.

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
`VIEW_TEAM`, `EDIT_TEAM`, and `APPROVE`.

## Obtain a JWT using an LDAP password

```bash
curl -s   -X POST http://localhost:8081/realms/employee-demo/protocol/openid-connect/token   -H "Content-Type: application/x-www-form-urlencoded"   -d "client_id=employee-demo"   -d "client_secret=employee-demo-secret"   -d "grant_type=password"   -d "username=bob"   -d "password=demo"
```

The access token should contain LDAP-derived claims such as:

```json
{
  "employeeId": "1002",
  "department": "PAYMENTS",
  "location": "SG",
  "managerDn": "uid=alice,ou=People,dc=demo,dc=local",
  "jobTitle": "Payments Manager",
  "groups": [
    "/Department-Payments",
    "/Location-Singapore",
    "/Function-Managers",
    "/Team-Bob",
    "/Team-Alice-Department"
  ]
}
```

and effective roles under `realm_access.roles`.

## Demo change: prove synchronization

For example, edit `ldap/bootstrap.ldif` before the first startup, or modify LDAP
with `ldapmodify`, then run a Keycloak synchronization.

A useful live demo is to move Bob from `Function-Managers` to
`Function-Employees` in LDAP and sync again. His effective Keycloak roles then
change because his LDAP group membership changed.

## Reset everything

OpenLDAP bootstrap LDIF is processed only when its data volume is empty, and
Keycloak startup import skips an already-existing realm. To return to the
original sample data:

```bash
docker compose down -v
docker compose up -d
```

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
