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
├── admin
│   ├── api
│   ├── dto
│   └── service
├── audit
└── config
```

These are packages, not Maven modules.

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
ExternalAuthorityMapper
AuthorizationCacheInvalidator
AuthorizationAuditPublisher
```

## Default implementations

```text
DatabaseEntitlementProvider
DatabaseResourceRuleProvider
DefaultPermissionMatcher
UrlResourcePatternMatcher
UiResourcePatternMatcher
IdentitySynchronizationProvider (unsupported default bean)
DatabaseExternalAuthorityMapper
DefaultAuthorizationCacheInvalidator
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
- admin REST API
- framework admin seed definitions

Back off when the application supplies its own SPI bean.

## Source enum

Core may define:

```text
DATABASE
KEYCLOAK
```

but must contain no Keycloak client classes.

If `source=keycloak` and the optional Keycloak integration is absent, fail startup with a clear message.

## Functional requirement

Core must be independently useful. DB-only mode must run end-to-end without `authorization-keycloak` or `authorization-admin-ui`.
