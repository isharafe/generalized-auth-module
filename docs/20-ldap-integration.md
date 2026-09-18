# Optional LDAP Integration

Module:

```text
authorization-ldap
```

The module activates when it is present, `authorization.source=ldap`, and
`authorization.ldap.enabled=true` (the default). It contributes a JNDI LDAP directory client, a
supported `IdentitySynchronizationProvider`, and optional scheduled synchronization. Core fails
startup with an actionable error when LDAP is selected without a supported provider.

## Responsibility boundary

```text
Spring Security LDAP authentication
  |
authenticated stable LDAP identity
  |
authorization-ldap synchronization
  |
local AUTH_* tables and caches
  |
authorization-core runtime authorization
```

LDAP supplies identity attributes, group membership, and configured attribute values. Local Roles,
PermissionGroups, Permissions, ResourceRules, and authorization decisions remain application-owned.
The module does not register a `SecurityFilterChain` or replace Spring Security authentication.

## Dependency and connection

```xml
<dependency>
  <groupId>io.github.isharafe</groupId>
  <artifactId>authorization-ldap</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
authorization:
  source: ldap
  ldap:
    urls: [ldaps://directory.example.com:636]
    base-dn: dc=example,dc=com
    bind-dn: cn=authorization-sync,ou=services,dc=example,dc=com
    bind-password: ${AUTHORIZATION_LDAP_BIND_PASSWORD}
    issuer: company-ldap

    user:
      base-dn: ou=people
      search-filter: "(objectClass=inetOrgPerson)"
      identity-attribute: entryUUID
      username-attribute: uid
      email-attribute: mail
      first-name-attribute: givenName
      last-name-attribute: sn
      member-of-attribute: memberOf
      authority-attributes: [department, employeeType]

    group:
      base-dn: ou=groups
      search-filter: "(member={0})"
      name-attribute: cn

    sync:
      page-size: 500
      paged-results: true
      full-cron: "0 0 2 * * *"
      incremental-cron: "-"
```

All search bases are relative to `base-dn`. Multiple URLs provide JNDI provider failover. Bind DN
and password may both be omitted for anonymous-read directories. Use a least-privilege read-only
account and LDAPS in production; a plain `ldap://` service bind sends credentials without transport
protection unless the surrounding network supplies it. The default client supports LDAP and LDAPS,
not an explicit StartTLS upgrade.

The default client uses escaped LDAP filter values for targeted stable-ID and group-membership
searches. Connection/read timeouts are mandatory positive durations. Referral behavior is one of
`ignore`, `follow`, or `throw`. Paged results use the LDAP paging control; set
`sync.paged-results=false` for a small directory that does not implement it.

## Stable identity

The local stable key is:

```text
(authorization.ldap.issuer, configured LDAP identity attribute)
```

When `issuer` is omitted, it defaults to the first normalized LDAP URL plus `base-dn`. Explicitly
set it when multiple environments should share or deliberately distinguish identity namespaces.
Use an immutable directory identifier such as `entryUUID` rather than username, email, or DN.

Active Directory commonly exposes a binary `objectGUID`:

```yaml
authorization:
  ldap:
    user:
      identity-attribute: objectGUID
      identity-attribute-binary: true
```

Binary attribute values are represented as unpadded base64url locally, and targeted lookup decodes
that representation back into a binary LDAP filter assertion.

## Authentication integration

The consuming application configures Spring Security LDAP authentication. Its
`SpringAuthenticationIdentityResolver` must return the same LDAP issuer and stable subject that the
synchronizer stores. For example, an application-specific authenticated principal can retain the
`entryUUID`, and a resolver can expose it:

```java
@Bean
SpringAuthenticationIdentityResolver ldapIdentityResolver() {
  return authentication -> {
    CompanyLdapPrincipal principal = (CompanyLdapPrincipal) authentication.getPrincipal();
    return new AuthenticatedIdentity("company-ldap", principal.entryUuid(), authentication.getName());
  };
}
```

After successful authentication, the application may call targeted synchronization with that
identity before redirecting to protected pages. Alternatively, run a full sync before users access
the application. A synchronization failure must not be interpreted as an empty entitlement set.

The framework intentionally does not derive the stable subject from username on every request:
username is mutable and LDAP must not become a request-time authorization dependency.

## Group and attribute authorities

Two group-discovery strategies can run together:

- values of the configured user `member-of-attribute`
- a group subtree search whose filter contains the escaped user-DN placeholder `{0}`

The default group authority is the configured `name-attribute` such as `cn`. Set
`group.use-dn-as-authority=true` when mappings should use complete group DNs. Set either strategy's
attribute/filter to an empty string to disable it.

Configured user authority attributes become exact authority values:

```text
department=Payroll
employeeType=Contractor
```

Seed or create explicit mappings:

```yaml
authorization:
  seed:
    external-authority-mappings:
      - source-system: LDAP
        authority-type: GROUP
        authority: Finance-Managers
        target:
          type: ROLE
          code: FINANCE_MANAGER

      - source-system: LDAP
        authority-type: ATTRIBUTE
        authority: department=Payroll
        target:
          type: PERMISSION_GROUP
          code: PAYROLL_ACCESS
```

Mapping values are exact. LDAP groups and attributes never create Permissions or ResourceRules and
never directly contain URL/UI permission patterns.

## User enabled state

LDAP users are enabled by default. A directory can expose an enabled/status attribute:

```yaml
authorization:
  ldap:
    user:
      enabled-attribute: employeeStatus
      enabled-values: [active, enabled]
```

The match is case-insensitive. When `enabled-values` is empty, any nonblank attribute value means
enabled. Complex computed status schemes, such as Active Directory bit masks, can be implemented by
supplying a custom `LdapDirectoryClient` bean.

## Synchronization behavior

Full reconciliation:

1. acquires the shared `GLOBAL_IDENTITY_SYNC` pessimistic database lock
2. pages through LDAP users
3. reads configured group and attribute authorities
4. resolves explicit local mappings
5. upserts local identity attributes
6. adds desired `IDENTITY_SYNC` Role/PermissionGroup assignments
7. removes stale `IDENTITY_SYNC` assignments
8. preserves all `MANUAL` and `SEED` assignments
9. resolves pending seed assignments
10. disables unseen LDAP identities and removes only their sync-owned assignments
11. increments entitlement versions when authorization state changes
12. commits and invalidates affected local entitlement caches
13. records status and safe audit events

Targeted synchronization looks up one user by stable ID. When that identity no longer exists, the
local user is marked missing using the same ownership rules. Generic LDAP has no portable
modified-since cursor, so `synchronizeIncremental()` deliberately runs a correctness-preserving full
scan and reports `INCREMENTAL_FULL_SCAN`.

The scheduler is enabled by default, but both cron expressions default to `-`. Setting
`authorization.ldap.sync.enabled=false` removes scheduler creation while retaining manual admin
full, incremental, and targeted actions.

## Overrides and operational safety

A consuming application may provide its own `LdapDirectoryClient` bean for a vendor-specific SDK,
StartTLS, custom paging, nested-group expansion, computed account state, or additional directory
schemas. A custom `IdentitySynchronizationProvider` replaces the supplied LDAP synchronizer.

Audit and status details never contain bind passwords. Normal authorization always reads local
cache/database state and never opens an LDAP connection.
