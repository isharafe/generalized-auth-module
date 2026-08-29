# authorization-core

This module contains the complete default DB-backed implementation.

Suggested packages:

```text
com.example.authorization
├── domain
├── engine
├── spi
├── security
├── persistence
│   ├── entity
│   ├── repository
│   └── service
├── seed
├── cache
├── audit
└── config
```

These are packages, not Maven modules. Administrative controllers, DTOs, management services, UI resources, admin configuration, and framework-admin seed definitions belong to the optional `authorization-admin` module.

## Source conventions

Use Lombok where it removes mechanical code without weakening the API:

- `@RequiredArgsConstructor` for constructor-injected services and engine components
- `@Getter`/`@Setter` for mutable configuration, seed-binding, and persistence properties
- `@NoArgsConstructor(access = PROTECTED)` for JPA types that require it
- `@EqualsAndHashCode` for embeddable identifiers
- `@Slf4j` for class loggers

Keep immutable domain models and DTOs as records. Avoid `@Data` on JPA entities, and suppress
setters for database identifiers, optimistic-lock versions, counters, and managed associations.

## Public SPIs

At minimum:

```text
EntitlementProvider
ResourceRuleProvider
PermissionMatcher
ResourcePatternMatcher
IdentitySynchronizationProvider
IdentityChangeEventProcessor
ExternalAuthorityMapper
AuthorizationCacheInvalidator
AuthorizationInvalidationPublisher
AuthorizationAuditPublisher
AuthorizationObservation
```

## Default implementations

```text
DatabaseEntitlementProvider
DatabaseResourceRuleProvider
DefaultPermissionMatcher
UrlResourcePatternMatcher
UiResourcePatternMatcher
IdentitySynchronizationProvider (unsupported default bean)
DatabaseIdentityChangeEventProcessor
DatabaseExternalAuthorityMapper
DefaultAuthorizationCacheInvalidator
DatabaseAuthorizationInvalidationPublisher (opt-in)
MicrometerAuthorizationObservation (when a MeterRegistry exists)
```

## Auto-configuration

Default:

```yaml
authorization:
  enabled: true
  source: database
```

Auto-configure when applicable:

- authorization Flyway
- repositories/services
- DB providers
- caches
- authorization manager
- seed processor
- provider-neutral identity-change event processor and durable replay ledger
- low-cardinality Micrometer observations when a registry is available
- optional database-backed cross-instance cache invalidation

Back off when the application supplies its own SPI bean.

## Source selection

The `authorization.source` property is a string. The supplied implementations use:

```text
DATABASE
KEYCLOAK
LDAP
```

Core contains no Keycloak or LDAP client classes. A custom source name is also valid when the
application supplies a supported `IdentitySynchronizationProvider`.

If `source=keycloak` and the optional Keycloak integration is absent, fail startup with a clear message.
The same guard applies to `source=ldap` when `authorization-ldap` is absent or invalid.

## Functional requirement

Core must be independently useful. DB-only mode must run end-to-end without
`authorization-keycloak`, `authorization-ldap`, or `authorization-admin`.
