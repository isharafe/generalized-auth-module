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

  ui-api:
    enabled: true
    endpoint: /authorization/ui/permissions

  distributed-invalidation:
    enabled: false
    instance-id: ${HOSTNAME:replace-with-a-unique-instance-id}
    poll-interval: 1s
    retention: 24h
    batch-size: 500
```

Distributed invalidation remains disabled for a single-instance deployment. Enable it on every
instance sharing the authorization database and give every running instance a unique ID. Poll
interval and retention must be positive, batch size must be between 1 and 10,000, and instance ID
must contain 1-100 characters. The generated UUID default is unique per application start; an
explicit stable pod/host identifier is easier to operate. Retention bounds replay and storage, so it
must exceed the longest expected instance outage when retained invalidations need to be replayed.

Micrometer collection activates automatically when the application provides a `MeterRegistry`.
Spring Boot Actuator plus the selected registry implementation controls how the metrics are exposed.
No authorization property is required. Metrics include decisions, logical persistence operations,
Keycloak/LDAP requests, token-cache usage, synchronization, and post-login initialization. Network
timers count each physical page and retry; normal authorization requests remain local and therefore
must not increment the external-request counters.

The UI API returns only enabled permissions whose resource type is `UI`, together with the current
entitlement version. It requires authentication when core supplies the cookie OAuth2 security
chain, sends `Cache-Control: no-store`, returns 401 without an authenticated stable identity, and
returns 503 when entitlement infrastructure is unavailable. Applications that provide their own
`SecurityFilterChain` must require authentication for the configured endpoint themselves.

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

## Framework-managed cookie OAuth2

To let core own standard browser authentication, configure the matching Spring Security OAuth2
client and resource-server settings:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            provider: keycloak
            client-id: application-web
            client-secret: ${OAUTH_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            scope: openid,profile,email
        provider:
          keycloak:
            issuer-uri: https://keycloak.example.com/realms/COMPANY
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example.com/realms/COMPANY

authorization:
  security:
    cookie-oauth2:
      enabled: true
      registration-id: keycloak
      login-success-uri: /
      csrf-endpoint: /authorization/security/csrf
      refresh-endpoint: /authorization/security/token/refresh
      logout-endpoint: /authorization/security/logout
      access-token-cookie:
        secure: true
      refresh-token-cookie:
        path: /authorization/security
        secure: true
      id-token-cookie:
        secure: true
      csrf:
        secure: true
      logout:
        mode: LOCAL
        revoke-refresh-token: true
        post-logout-redirect-uri: "{baseUrl}/"
```

Production HTTPS deployments should keep every cookie's `secure` setting true. Local HTTP demos
must explicitly turn it off.

`LOCAL` logout clears this application's cookies and leaves the identity-provider SSO session.
`OIDC` also redirects through the provider's RP-initiated logout endpoint and therefore requires
provider `end_session_endpoint` metadata. Refresh-token revocation is independently controlled by
`revoke-refresh-token` and runs only when the provider advertises `revocation_endpoint`.

The endpoint and cookie-path relationships are startup-validated. Cookie OAuth2 mode remains
disabled by default because it requires a concrete client registration. With it disabled, the
application must supply a `SecurityFilterChain`; an application-defined chain is also the complete
override when cookie OAuth2 is enabled.

## Nuxt/Nitro client

The source-only module under `integrations/authorization-nuxt` uses these server endpoints through
a same-origin Nitro proxy. The minimal module configuration is:

```ts
export default defineNuxtConfig({
  modules: ['@isharafe/authorization-nuxt'],
  authorizationNuxt: {
    backendBaseUrl: process.env.AUTHORIZATION_BACKEND_URL!,
    publicBaseUrl: process.env.NUXT_PUBLIC_BASE_URL!,
    loginEndpoint: '/oauth2/authorization/keycloak'
  }
})
```

Keep `backendBaseUrl` server-only. `publicBaseUrl` is the browser-visible Nuxt origin used for
trusted forwarded headers; enable `server.forward-headers-strategy=framework` in Spring so OAuth2
redirect URIs use that origin. Override endpoint paths in both Spring and Nuxt when changing a
default. The refresh-token cookie can remain scoped to `/authorization/security`: SSR fails closed
when the access token has expired, then the browser performs refresh during hydration.

See [the Nuxt/Nitro integration guide](23-nuxt-integration.md) for the full option and usage
reference.


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

`AuthorizationObservation` can be replaced to integrate another telemetry system. A custom
cross-instance transport supplies an `AuthorizationInvalidationPublisher` plus an inbound adapter
that invokes `PublishingAuthorizationCacheInvalidator.receive(...)`, with the built-in database
option left disabled. Applications that replace `AuthorizationCacheInvalidator` own both local and
cross-instance invalidation behavior.
