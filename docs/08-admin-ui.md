# Optional Admin UI

Module:

```text
authorization-admin-ui
```

Preferred implementation:

```text
React + TypeScript
```

Use another modern SPA stack only if repository constraints require it.

## Packaging

Frontend build output is packaged into the JAR and served by the consuming Spring Boot application.

Default configurable path:

```text
/authorization-admin/
```

Use same origin/API to avoid CORS complexity.

## UI uses API only

The UI calls the admin REST API in `authorization-core`.

It must not access repositories/DB and must not contain Keycloak SDK logic.

## Pages

### Dashboard

Show:

- source (DATABASE / KEYCLOAK)
- counts
- sync status if available
- recent denies/audit summary

### Users

Show:

- identity source
- issuer/subject
- username/email/name
- enabled
- roles
- permission groups
- effective permissions
- assignment sources

Use badges:

```text
SEED
MANUAL
IDENTITY_SYNC
```

Externally synchronized identity attributes are read-only.

### Roles

CRUD code/name/description/enabled and permission-group membership.

`code` becomes read-only after create.

### Permission Groups

CRUD and permission assignment.

### Permissions

Structured URL editor:

```text
code
name
resource type
permission pattern (METHOD:/path)
enabled
```

Show preview like:

```text
URL:PUT:/api/employees/**
```

### Resource Rules

Structured editor with conflict warnings.

### External Authority Mappings

Example display:

```text
KEYCLOAK GROUP /AD/Finance-Managers
  -> ROLE FINANCE_MANAGER
```

### Synchronization

Only when supported by `/capabilities`.

Show status/history and actions for single-user/incremental/full sync.

### Authorization Test

Let admin choose identity + method + path and display the explain result/path.

### Audit

Paginated, filterable events.

## Capability-driven behavior

Call:

```text
GET /authorization-admin/api/capabilities
```

Hide provider-specific screens when not supported.

## UI security

Protect both UI entry path and admin APIs using authorization framework permissions. Do not introduce a second hardcoded admin authorization scheme.
