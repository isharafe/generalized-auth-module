# Database and Flyway

## Tables

Use `AUTH_` prefix.

Required baseline tables:

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
```

Add a DB lock table only if the chosen initialization/sync locking implementation needs it.

## Constraints

Unique at minimum:

```text
AUTH_ROLE.CODE
AUTH_PERMISSION_GROUP.CODE
AUTH_PERMISSION.CODE
AUTH_USER(EXTERNAL_ISSUER, EXTERNAL_SUBJECT)
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

Create an integration test where:

```text
application V1
authorization V1
```

both execute and are recorded in separate history tables.

## Demo database

The runnable demo uses H2 by default. Baseline migrations must work on H2. Keep production DB-specific SQL isolated when necessary.
