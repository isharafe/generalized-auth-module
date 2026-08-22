# Optional Keycloak Integration

Module:

```text
authorization-keycloak
```

Enabled only when dependency exists and configuration selects Keycloak source.

## Architecture

```text
AD/LDAP
  |
Keycloak federation
  |
Keycloak Admin API
  |
authorization-keycloak sync
  |
local AUTH_* tables
  |
authorization-core runtime cache/DB
```

Normal request authorization remains local.

## AD example

```text
John Smith
sAMAccountName = john.smith
department = Finance
employeeNumber = 12345
memberOf:
  Finance-Managers
  Singapore-Employees
```

AD groups should express external/business identity facts rather than application URL permissions.

## Keycloak federation example

Conceptual configuration:

```text
Realm: COMPANY
User Federation: LDAP / Active Directory
Users DN: OU=Employees,DC=company,DC=com
Username LDAP Attribute: sAMAccountName
```

Mappers:

```text
department -> Keycloak user attribute
employeeNumber -> Keycloak user attribute
LDAP groups -> Keycloak groups
```

Result:

```text
john.smith
 groups:
   /AD/Finance-Managers
   /AD/Singapore-Employees
```

## Explicit mappings

Support:

```text
KEYCLOAK_GROUP -> ROLE
KEYCLOAK_GROUP -> PERMISSION_GROUP
KEYCLOAK_ROLE  -> ROLE
KEYCLOAK_ROLE  -> PERMISSION_GROUP
```

Example seed:

```yaml
authorization:
  seed:
    external-authority-mappings:
      - source-system: KEYCLOAK
        authority-type: GROUP
        authority: /AD/Finance-Managers
        target:
          type: ROLE
          code: FINANCE_MANAGER

      - source-system: KEYCLOAK
        authority-type: ROLE
        authority: payroll-approver
        target:
          type: ROLE
          code: PAYROLL_APPROVER
```

## Service account

Use a dedicated confidential client such as:

```text
authorization-sync-service
```

Use client credentials and least privilege. Never require `realm-admin` if narrower permissions work.

Secrets come from environment/secret configuration, not seed data/code.

## Sync algorithm

For each external user:

1. fetch user and stable Keycloak identity
2. fetch configured user attributes
3. fetch groups
4. fetch relevant roles
5. convert to provider-neutral `ExternalAuthority`
6. apply local `ExternalAuthorityMapping`
7. upsert `AUTH_USER`
8. compute desired `IDENTITY_SYNC` role/group assignments
9. add missing sync-owned assignments
10. remove stale sync-owned assignments
11. preserve `MANUAL` and `SEED`
12. resolve pending seed assignments
13. increment entitlement version if authorization changed
14. commit
15. invalidate affected cache
16. record audit/sync result

## Periodic synchronization

Support:

- initial/full reconciliation
- configurable periodic full reconciliation
- incremental/targeted synchronization where practical
- single-user sync

For multi-pod apps, only one instance performs a given global sync at once.

## Event-driven refresh

Optional later optimization.

```text
provider event -> generic IdentityChangeEvent -> targeted sync -> commit -> invalidate cache
```

Events may be lost/duplicated/reordered, so periodic reconciliation remains the correctness safety net.

## Runtime

Even with `source=keycloak`:

```text
request
 -> Spring Security authentication (often Keycloak JWT)
 -> AuthenticatedIdentity
 -> local entitlement cache
 -> local DB on miss
 -> AuthorizationEngine
```

Do not call Keycloak Admin API for each application request.
