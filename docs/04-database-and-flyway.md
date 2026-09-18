# Database and Flyway

## Tables

Use `AUTH_` prefix.

Tables after all current authorization migrations:

```text
AUTH_USER
AUTH_ROLE
AUTH_PERMISSION_GROUP
AUTH_PERMISSION
AUTH_RESOURCE_RULE
AUTH_PERMISSION_GROUP_PERMISSION
AUTH_ROLE_PERMISSION_GROUP
AUTH_USER_ROLE
AUTH_USER_PERMISSION_GROUP
AUTH_EXTERNAL_AUTHORITY_MAPPING
AUTH_PENDING_USER_ASSIGNMENT
AUTH_SEED_HISTORY
AUTH_AUDIT_EVENT
AUTH_SYNC_STATE
AUTH_IDENTITY_CHANGE_EVENT
AUTH_CACHE_INVALIDATION
```

`AUTH_SYNC_STATE` contains the `GLOBAL_IDENTITY_SYNC` row used both for synchronization status and a pessimistic database lock. Holding that row lock serializes full/targeted synchronization across application instances sharing the database.

It also contains `GLOBAL_SEED_INITIALIZATION`. The seed initialization service locks this row before
checking/applying the combined seed, serializing concurrent application starts that share a database.

`AUTH_IDENTITY_CHANGE_EVENT` is the provider-neutral event-processing ledger. Its
`(SOURCE_SYSTEM, EXTERNAL_EVENT_ID)` unique key makes callback delivery idempotent across application
instances. Status, attempt count, claim time, completion time, and safe failure class support replay,
retry, and stale-claim recovery without storing callback secrets or request bodies.

`AUTH_CACHE_INVALIDATION` is the optional database transport for provider-neutral invalidation
events. Polling instances skip their own origin, apply other instances' identity/rule/all-cache
events locally, and remove expired records according to the configured retention period.

## Constraints

Unique at minimum:

```text
AUTH_ROLE.CODE
AUTH_PERMISSION_GROUP.CODE
AUTH_PERMISSION.CODE
AUTH_USER(EXTERNAL_ISSUER, EXTERNAL_SUBJECT)
AUTH_IDENTITY_CHANGE_EVENT(SOURCE_SYSTEM, EXTERNAL_EVENT_ID)
```

All mapping tables require appropriate unique constraints.

## Optimistic locking

Use JPA `@Version` for mutable admin-managed entities, including users, roles, groups, permissions, rules, and external mappings.

Persistence entities use focused Lombok annotations for accessors and required JPA constructors.
Do not use entity-wide `@Data`, `@Value`, or `@Builder`: generated equality, string rendering,
or unrestricted mutation can traverse lazy relationships or violate persistence lifecycle rules.
Database identifiers and `@Version` fields expose getters only. Relationship collections expose
getters for controlled in-place mutation, not replacement setters. Embeddable composite IDs use
Lombok-generated all-arguments/protected no-arguments constructors and value-based
`equals`/`hashCode`.

Stale admin updates should become HTTP 409.

## Assignment source

`AUTH_USER_ROLE` and `AUTH_USER_PERMISSION_GROUP` store:

```text
ASSIGNMENT_SOURCE
SOURCE_REFERENCE
```

## Flyway separation

Application:

```text
classpath:db/migration
flyway_schema_history
```

Authorization:

```text
classpath:db/authorization/migration
authorization_flyway_schema_history
```

Use two Flyway instances. Both can contain `V1`, `V2`, etc.

## Startup order

```text
DataSource
 -> application Flyway (if any)
 -> authorization Flyway
 -> authorization repositories
 -> seed engine
 -> caches/providers
 -> ready
```

The critical rule is that authorization repositories/seed code cannot run before authorization migration completes.

## Tests

Integration coverage verifies:

```text
application V1
authorization V1
```

both execute and are recorded in separate history tables.

It also migrates an existing V1 database through V2/V3, verifies existing authorization data is
preserved, and asserts the final lock/invalidation schema. The V1 baseline requires each permission
code to start with its stored resource type.

## Demo database

The runnable demo uses H2 by default. Baseline migrations must work on H2. Keep production DB-specific SQL isolated when necessary.
