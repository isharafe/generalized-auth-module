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

If `source=keycloak` is selected but the optional module/provider is absent, startup fails with a
direct actionable error. `source=database` continues to work without the Keycloak module.

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
