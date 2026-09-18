# Phase 1 Implementation

## Delivered

Phase 1 provides a functional DB-only authorization path in `authorization-core` and an H2 demo. The core contains public domain/SPIs, deterministic lock selection, Spring `PathPattern` matching, an `AuthorizationManager<RequestAuthorizationContext>`, top-level JPA entities and repositories, separate Flyway initialization, validated YAML/Java seed merging, local targeted caches, audit persistence, and Spring Boot auto-configuration.

The demo proves public, authenticated-only, permission-view, permission-edit, query-string safety, infrastructure-failure, application seed assignments, and independent application/component Flyway histories.

## Persistence layout

Each entity and embeddable ID is a top-level class in `io.github.isharafe.authorization.persistence.entity`. This keeps imports, logs, tests, and future entity evolution straightforward. Numeric IDs stay internal and stable logical codes remain the configuration/API identifiers.

Mechanical Java boilerplate is generated with Lombok. The parent POM centralizes the Lombok
version and compiler annotation-processor path, while `authorization-core` declares Lombok as an
optional, provided dependency. Entities use focused getter/setter/constructor annotations rather
than `@Data`, preserving read-only identifiers and versions and preventing generated equality or
string methods from traversing lazy associations. Immutable domain values remain records.

## Runtime wiring

Consumers retain ownership of authentication and their `SecurityFilterChain`. They inject `DynamicRequestAuthorizationManager` into `authorizeHttpRequests` and install the provided access-denied handler for the 503 mapping. Default SPI beans back off when a consumer supplies a replacement.

## Phase boundaries

Phase 1 is limited to the authorization runtime and its extension SPIs. Phase 2 management REST functionality and Phase 3 SPA functionality are delivered together by the optional `authorization-admin` module, as documented in `docs/17-phase-2-implementation.md` and `docs/18-phase-3-implementation.md`. Keycloak synchronization remains Phase 4.

## Original Phase 1 operational boundary

Phase 1 initially shipped only local cache invalidation and transaction/uniqueness-based seed
safety. Phase 7 subsequently added the provider-neutral/database distributed invalidation option,
the seed initialization database lock, and concurrent verification. See
[the Phase 7 summary](22-phase-7-implementation.md).
