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

  admin:
    api:
      enabled: true
      base-path: /authorization-admin/api
```

## Keycloak source

Requires `authorization-keycloak`.

```yaml
authorization:
  source: keycloak

  keycloak:
    enabled: true
    base-url: https://keycloak.example.com
    realm: COMPANY
    client-id: authorization-sync-service
    client-secret: ${AUTHORIZATION_KEYCLOAK_CLIENT_SECRET}

    sync:
      enabled: true
      incremental-cron: "0 */5 * * * *"
      full-cron: "0 0 2 * * *"

    http:
      connect-timeout: 5s
      read-timeout: 15s
```

If `source=keycloak` but the integration module is absent, fail startup with a direct actionable error.

## Admin UI

When optional UI dependency is present:

```yaml
authorization:
  admin:
    ui:
      enabled: true
      base-path: /authorization-admin
```

The UI base path is normalized without a trailing slash. The packaged SPA loads relative assets and obtains the configured API/UI paths from `<ui-base-path>/config`, so the API and UI paths may be changed independently. The framework seed uses the configured paths when contributing admin permissions.

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

## Custom provider override

Auto-configuration must back off when a consuming app declares its own provider bean, e.g.:

```java
@Bean
EntitlementProvider entitlementProvider() {
    return new CompanyEntitlementProvider(...);
}
```
