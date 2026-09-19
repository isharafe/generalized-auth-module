# Keycloak + OpenLDAP Employee Demo

Local identity infrastructure for the authorization examples. It runs PostgreSQL, OpenLDAP, and
Keycloak with a preconfigured `employee-demo` realm, LDAP federation, sample employees, groups,
roles, and authorization-demo clients.

All credentials are intentionally weak and are for local use only.

## Quick start

From this directory, start only the identity services:

```bash
docker compose up -d
docker compose logs -f keycloak-init
```

Setup is complete when the log contains:

```text
Authorization synchronization Admin API access verified.
LDAP federation demo is ready.
```

Open <http://localhost:8081/admin/> and select the `employee-demo` realm.

| Service | Address | Credentials |
| --- | --- | --- |
| Keycloak admin | <http://localhost:8081/admin/> | `admin` / `admin` |
| LDAP from host | `ldap://localhost:1389` | `cn=admin,dc=demo,dc=local` / `admin` |
| LDAP in Compose | `ldap://ldap:389` | Same bind credentials |

To start an application with the identity services:

```bash
# Spring-only demo at http://localhost:8080/demo-ui/
docker compose --profile authorization-demo up -d --build

# Spring + Nuxt demo at http://localhost:3000/
docker compose --profile authorization-nuxt-demo up -d --build
```

Containerized applications use `host.docker.internal:8081` as the browser-visible issuer. On native
Linux, add `127.0.0.1 host.docker.internal` to `/etc/hosts` if that name does not resolve.

## Application demo users

These Keycloak-local users all have password `demo`:

| User | External authority |
| --- | --- |
| `emma` | Group `/authorization-demo/hr-analysts` |
| `michael` | Group `/authorization-demo/hr-managers` and inherited role `people-manager` |
| `olivia` | Group `/authorization-demo/authorization-administrators` |

The authorization demos explicitly map those authorities to application-owned roles. Keycloak
groups and roles never become application permissions automatically.

## LDAP sample data

Every LDAP employee has password `demo`.

| User | Function | Department | Location | Manager |
| --- | --- | --- | --- | --- |
| `alice` | Department Head | PAYMENTS | SG | — |
| `bob` | Manager | PAYMENTS | SG | Alice |
| `john` | Employee | PAYMENTS | SG | Bob |
| `mary` | Employee | PAYMENTS | LONDON | Bob |
| `susan` | HR Admin | HR | SG | — |
| `david` | Employee | FINANCE | TOKYO | — |

Users are stored under `ou=People,dc=demo,dc=local` and groups under
`ou=Groups,dc=demo,dc=local`. The standard LDAP `manager` attribute stores reporting relationships.

Inspect the directory with:

```bash
docker exec keycloak-demo-ldap ldapsearch -x \
  -H ldap://localhost:389 \
  -D "cn=admin,dc=demo,dc=local" \
  -w admin \
  -b "dc=demo,dc=local"
```

## Federation model

Keycloak imports the LDAP users in read-only mode; passwords continue to be validated by LDAP.
The realm maps common LDAP attributes such as `uid`, `mail`, `departmentNumber`, `manager`, and
`title` to Keycloak user fields or attributes.

One group mapper imports department, location, function, and team groups. Function groups grant
business roles:

```text
Function-Employees        -> employee
Function-Managers         -> employee, people-manager, request-approver
Function-Department-Heads -> employee, people-manager, department-head, request-approver
Function-HR-Admins        -> employee, hr-administrator, request-approver
```

Applications still decide what those roles mean. The authorization examples retrieve selected
groups and realm roles through a separate least-privilege service account, map them to local
assignments, and authorize normal requests locally.

The browser client deliberately requests only `openid`. Access tokens retain stable identity and
security context but omit LDAP attributes, groups, roles, and profile details.

## Synchronization

`keycloak/employee-demo-realm.json` defines the realm, clients, federation provider, and mappers.
The one-time `keycloak-init` service runs `keycloak/sync-ldap.sh` to:

1. wait for Keycloak;
2. enforce and verify the synchronization service account's read-only access;
3. test LDAP authentication;
4. synchronize groups and users; and
5. refresh memberships.

Rerun it after changing live LDAP data:

```bash
docker compose run --rm keycloak-init
```

You can also synchronize from `User federation -> demo-openldap` in the Keycloak admin console.

## Reset

Realm import and LDAP bootstrap apply only to empty data volumes. After changing the realm JSON or
LDAP fixture, reset the demo:

```bash
./reset-demo-data.sh
docker compose up -d
```

The script removes only this Compose project's containers, network, and three data volumes. It does
not delete images or repository files.

## Security

This demo uses plain LDAP, trivial passwords, an LDAP admin bind, Keycloak `start-dev`, and checked-in
client secrets. Production deployments must use LDAPS or StartTLS, least-privilege service
accounts, secret management, production Keycloak settings, and an appropriately secured directory.

For application authorization behavior, see the
[Spring demo](../authorization-demo/README.md) or
[Spring + Nuxt demo](../authorization-nuxt-demo/README.md).
