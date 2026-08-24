# Phase 1 Implementation

## Delivered

Phase 1 provides a functional DB-only authorization path in `authorization-core` and an H2 demo. The core contains public domain/SPIs, deterministic lock selection, Spring `PathPattern` matching, an `AuthorizationManager<RequestAuthorizationContext>`, top-level JPA entities and repositories, separate Flyway initialization, validated YAML/Java seed merging, local targeted caches, audit persistence, and Spring Boot auto-configuration.

The demo proves public, authenticated-only, permission-view, permission-edit, query-string safety, infrastructure-failure, framework-admin assignment, and independent application/component Flyway histories.

## Persistence layout

Each entity and embeddable ID is a top-level class in `com.example.authorization.persistence.entity`. This keeps imports, logs, tests, and future entity evolution straightforward. Numeric IDs stay internal and stable logical codes remain the configuration/API identifiers.

Mechanical Java boilerplate is generated with Lombok. The parent POM centralizes the Lombok
version and compiler annotation-processor path, while `authorization-core` declares Lombok as an
optional, provided dependency. Entities use focused getter/setter/constructor annotations rather
than `@Data`, preserving read-only identifiers and versions and preventing generated equality or
string methods from traversing lazy associations. Immutable domain values remain records.

## Runtime wiring

Consumers retain ownership of authentication and their `SecurityFilterChain`. They inject `DynamicRequestAuthorizationManager` into `authorizeHttpRequests` and install the provided access-denied handler for the 503 mapping. Default SPI beans back off when a consumer supplies a replacement.

## Phase boundaries

Phase 1 originally included only the admin capabilities endpoint. Phase 2 admin REST CRUD is delivered in `docs/17-phase-2-implementation.md`, and the Phase 3 SPA is delivered in `docs/18-phase-3-implementation.md`. Keycloak synchronization remains Phase 4.

## Current operational limitation

The cache is local to one application instance. Entitlement versions and targeted invalidation contracts are present, but distributed invalidation is intentionally deferred to production hardening. Seed concurrency relies on transactions and database uniqueness; a dedicated cross-pod initialization lock can be added during hardening if the target database requires it.
