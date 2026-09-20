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

- source (DATABASE / KEYCLOAK / LDAP)
- counts
- sync status if available
- recent audit activity

The application header shows the current authenticated user's display name, email/username, issuer,
and subject from `GET /current-user`.

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

On creation the editor accepts a local code and previews/submits the canonical
`<RESOURCE_TYPE>:<LOCAL_CODE>` identifier. The complete code and resource type are read-only after
creation.

Show preview like:

```text
URL:EMPLOYEE_EDIT
URL:PUT:/api/employees/**
```

### Resources

The Resources workspace is organized first by resource type so additional types can be added
without introducing unrelated top-level navigation. URL resources provide Coverage and Rules views;
UI resources currently provide the Rules view because opaque UI identifiers are not discoverable.

URL Coverage lists registered Spring MVC controller mappings, their application/framework origin,
effective access mode, enforcement source, and the winning security policy or resource rule.
Direct filter-chain decisions are distinguished from paths delegated to resource rules. A delegated
route with no matching rule is shown as `Default denied`, not as public; a route with no published
filter-chain metadata is shown as indeterminate/unknown. From an uncovered delegated route, an
administrator can open the rule editor with its method/path and safe `AUTHORIZED` defaults
prefilled; saving remains explicit.

The inventory covers `RequestMappingInfo` handler mappings in the current application context. It
does not claim static-resource handlers, arbitrary servlet registrations, functional router
predicates, or a separate management application context. Core describes its default security
chains automatically. Applications with custom chains must provide a `UrlSecurityPolicyContributor`
if they want the inventory to classify those chain decisions.

### Resource Rules

Structured editor for resource type, URL method/path or UI identifier, access mode, priority, and
enabled state. Conflicts are validated by the server and displayed through the common API error
handling; the UI does not perform an independent client-side conflict analysis.

### External Authority Mappings

Example display:

```text
KEYCLOAK GROUP /AD/Finance-Managers
  -> ROLE FINANCE_MANAGER

LDAP ATTRIBUTE department=Payroll
  -> PERMISSION_GROUP PAYROLL_ACCESS
```

### Synchronization

Only when supported by `/capabilities`.

Show the current status/details and actions for single-user/incremental/full sync.

### Authorization Test

Let admin choose identity + method + path and display the explain result/path.

### Audit

Paginated events with visible `DECISION`/`CHANGE` badges and filters for event kind, event type, actor, and target.

### Data Transfer

Download the complete portable authorization snapshot as versioned JSON. Import accepts a complete
export only and performs a destructive full replacement after file-shape checks, explicit user
acknowledgement, and a confirmation dialog. The page warns that omitting the current administrator
from the snapshot can remove their access.

## Capability-driven behavior

Call:

```text
GET /authorization-admin/api/capabilities
```

Hide external-mapping and synchronization navigation when their corresponding capability is not
supported.

## UI security

Protect both UI entry path and admin APIs using authorization framework permissions. Do not introduce a second hardcoded admin authorization scheme.

## Implemented deployment behavior

The Vite bundle uses relative assets and hash routes, so it can be served below the configured path without rebuilding. On startup it calls the protected `./config` endpoint to discover the independently configured API and UI base paths.

The Maven lifecycle installs pinned local Node/npm versions, restores dependencies with `npm ci`, runs frontend tests, builds the production bundle, and packages it in the module JAR. Consumers do not need a global frontend toolchain.

The server redirects `/authorization-admin` to `/authorization-admin/`, serves the packaged index
explicitly, and serves fingerprinted assets through Spring MVC's resource chain. Setting
`authorization.admin.ui.enabled=false` disables the module's server integration. The API can run
without the UI; a usable UI requires the admin API to remain enabled because startup loads both
`/capabilities` and `/current-user`, and every screen uses that API.
