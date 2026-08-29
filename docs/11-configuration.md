# Configuration

Use one property namespace:

```text
authorization
```

## DB-only mode

```yaml
authorization:
  enabled: true
  source: database
  default-decision: DENY

  database:
    migration:
      enabled: true
      location: classpath:db/authorization/migration
      history-table: authorization_flyway_schema_history

  seed:
    enabled: true
    fail-on-error: true
    locations:
      - classpath:authorization/seed.yml

  cache:
    resource-rules:
      enabled: true
      ttl: 30m
    entitlements:
      enabled: true
      ttl: 5m

  identity-events:
    processing-timeout: 5m
```

## Keycloak source

Add the optional `authorization-keycloak` dependency and configure:

```yaml
authorization:
  source: keycloak

  keycloak:
    enabled: true
    base-url: https://keycloak.example.com
    realm: COMPANY
    client-id: authorization-sync-service
    client-secret: ${AUTHORIZATION_KEYCLOAK_CLIENT_SECRET}
    issuer: https://keycloak.example.com/realms/COMPANY

    sync:
      enabled: true
      page-size: 100
      incremental-cron: "0 */5 * * * *"
      full-cron: "0 0 2 * * *"

    http:
      connect-timeout: 5s
      read-timeout: 15s
      max-attempts: 3
      retry-backoff: 250ms

    events:
      enabled: false
      path: /authorization/keycloak/events
      secret: ${AUTHORIZATION_KEYCLOAK_EVENT_SECRET}
      max-clock-skew: 5m
```

Required default-client settings are `base-url`, `realm`, `client-id`, and `client-secret`.
`issuer` is optional and defaults to `{base-url}/realms/{realm}`; explicitly set it when the JWT
issuer differs from the Admin API base URL. `page-size` must be between 1 and 1000, attempts must be
positive, timeouts must be positive, and retry backoff must not be negative. Invalid settings fail
startup before synchronization.

The schedule default is `-`, which disables that scheduled method. Setting
`authorization.keycloak.sync.enabled=false` disables scheduler creation while retaining manual
admin synchronization. The incremental entry point currently performs a correctness-preserving full
scan because the integrated Keycloak users endpoint provides no reliable modified-since cursor.

The event callback is disabled by default. When enabled, `secret` is required and must contain at
least 32 UTF-8 bytes, `path` must begin with `/`, and `max-clock-skew` must be positive. The callback
uses HMAC-SHA256 over the timestamp and exact request bytes and triggers targeted synchronization.
Configure the consuming application's Spring Security chain to permit only this callback path and
ignore CSRF only for that exact path; the controller performs HMAC authentication. Keep a periodic
`full-cron` schedule as the event-delivery correctness safety net. See
[the Keycloak integration guide](09-keycloak-integration.md#optional-event-callback) for the payload,
headers, signature contract, and response semantics.

`authorization.identity-events.processing-timeout` controls when an abandoned provider-neutral
event claim may be retried. It must be positive and defaults to five minutes.

If `source=keycloak` is selected but the optional module/provider is absent, startup fails with a
direct actionable error. `source=database` continues to work without the Keycloak module.

## LDAP source

Add the optional `authorization-ldap` dependency and configure:

```yaml
authorization:
  source: ldap

  ldap:
    enabled: true
    urls:
      - ldaps://directory.example.com:636
    base-dn: dc=example,dc=com
    bind-dn: cn=authorization-sync,ou=services,dc=example,dc=com
    bind-password: ${AUTHORIZATION_LDAP_BIND_PASSWORD}
    issuer: company-ldap
    connect-timeout: 5s
    read-timeout: 15s
    referral: ignore

    user:
      base-dn: ou=people
      search-filter: "(objectClass=inetOrgPerson)"
      identity-attribute: entryUUID
      identity-attribute-binary: false
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
      use-dn-as-authority: false

    sync:
      enabled: true
      page-size: 500
      paged-results: true
      incremental-cron: "-"
      full-cron: "0 0 2 * * *"
```

`urls`, `base-dn`, a valid user filter, and a stable identity attribute are required. Bind DN and
password are optional as a pair for directories that permit anonymous reads; production deployments
should normally use a least-privilege read-only bind over LDAPS. Configure `objectGUID` with
`identity-attribute-binary=true` for Active Directory. Disable either membership strategy by setting
its `member-of-attribute` or `group.search-filter` to an empty string. The group filter must retain
the escaped user-DN placeholder `{0}`.

LDAP `GROUP` and `ATTRIBUTE` authorities are explicitly mapped through local external-authority
mappings. Attribute authority values use `attribute=value`. The incremental entry point currently
performs a full scan because generic LDAP provides no portable modified-since cursor. See
[the LDAP integration guide](20-ldap-integration.md).

If `source=ldap` is selected but the optional module/provider is absent, startup fails with the same
actionable source guard used by Keycloak.

## Administration module

These properties are active when the optional `authorization-admin` dependency is present:

```yaml
authorization:
  admin:
    api:
      enabled: true
      base-path: /authorization-admin/api
    ui:
      enabled: true
      base-path: /authorization-admin
```

The API and UI server integrations can be enabled independently, but the SPA requires the API to be
enabled to function. The UI base path is normalized without a trailing slash. The packaged SPA
loads relative assets and obtains the configured API/UI paths from `<ui-base-path>/config`. The
admin module uses both paths when contributing its management permissions.

## Authentication remains separate

Example consuming application JWT authentication:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example.com/realms/COMPANY
```

This is Spring Security authentication configuration, not authorization source configuration.
`authorization.keycloak.issuer` identifies synchronized local users; the Resource Server
`issuer-uri` validates incoming JWTs. They normally have the same value.

## Custom provider override

Auto-configuration backs off when a consuming application declares its own applicable provider or
client bean. For example:

```java
@Bean
EntitlementProvider entitlementProvider() {
    return new CompanyEntitlementProvider(...);
}
```

A custom `KeycloakAdminClient` replaces the HTTP client while retaining the supplied synchronization
provider. A custom `IdentitySynchronizationProvider` replaces the Keycloak synchronizer and is also
used by the scheduler.
