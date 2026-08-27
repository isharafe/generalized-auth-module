# Optional Admin UI

Module:

```text
authorization-admin
```

Implementation:

```text
React + TypeScript
```


## Packaging

The Node/Vite project lives under `authorization-admin/frontend`, separate from the module's Maven Java source trees. Frontend build output is packaged into the JAR and served by the consuming Spring Boot application.

Default configurable path:

```text
/authorization-admin/
```

Use same origin/API to avoid CORS complexity.

## UI uses API only

The frontend calls the admin REST API colocated in `authorization-admin`. It communicates only through HTTP and never accesses repositories or the database directly.

The module's Java management services use repositories and SPIs supplied by `authorization-core`. The module owns no JPA entities, Flyway migrations, or Keycloak SDK logic.

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

Paginated events with visible `DECISION`/`CHANGE` badges and filters for event kind, event type, actor, and target.

## Capability-driven behavior

Call:

```text
GET /authorization-admin/api/capabilities
```

Hide provider-specific screens when not supported.

## UI security

Protect both UI entry path and admin APIs using authorization framework permissions. Do not introduce a second hardcoded admin authorization scheme.

## Implemented deployment behavior

The Vite bundle uses relative assets and hash routes, so it can be served below the configured path without rebuilding. On startup it calls the protected `./config` endpoint to discover the independently configured API and UI base paths.

The Maven lifecycle installs pinned local Node/npm versions, restores dependencies with `npm ci`, runs frontend tests, builds the production bundle, and packages it in the module JAR. Consumers do not need a global frontend toolchain.

The server redirects `/authorization-admin` to `/authorization-admin/`, serves the packaged index explicitly, and serves fingerprinted assets through Spring MVC's resource chain. Setting `authorization.admin.ui.enabled=false` disables the module's server integration.
