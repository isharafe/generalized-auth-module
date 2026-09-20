# Phase 3 Implementation

## Delivered

Phase 3 adds the functional React and TypeScript SPA to the optional `authorization-admin` module established in Phase 2. The same module owns the management API, DTOs, transactional services, admin seed contributor, UI server integration, and packaged frontend. It depends on `authorization-core`; JPA entities, repositories, migrations, runtime authorization, and provider SPIs remain in core.

The SPA includes:

- a source-aware dashboard with counts and recent audit activity
- user identity details, assignment-source badges, MANUAL assignment changes, and effective permissions
- CRUD screens for roles, permission groups, permissions, resource rules, and external mappings
- structured URL permission editing with a `METHOD:/path` preview
- authorization test/explain results
- paginated audit events with explicit DECISION/CHANGE badges and filtering
- synchronization status and actions only when the capabilities endpoint reports support
- current authenticated-user identity details in the application header
- full JSON export and destructive replacement import with acknowledgement and confirmation

Provider-specific navigation is capability-driven. Synchronized and seeded user assignments are displayed as read-only; only MANUAL assignments can be removed through the UI.

## Build and packaging

The self-contained Vite project lives under `authorization-admin/frontend`, separate from Maven's `src/main` and `src/test` Java trees. The module pins Node and npm through `frontend-maven-plugin`, uses `npm ci` from the frontend `package-lock.json`, runs Vitest during Maven's test phase, and builds the Vite production bundle during resource generation. Maven packages the result under:

```text
META-INF/resources/authorization-admin/
```

No globally installed Node or npm is required for a Maven build. Node, `node_modules`, Vite output, coverage, and Maven targets are ignored by Git.

The bundle uses relative asset URLs and hash-based routes. `AdminUiResourceConfiguration` serves assets from the configured path, while `AdminUiController` redirects the path without a trailing slash, serves the packaged index, and exposes protected runtime configuration.

## Configuration

Defaults:

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

The SPA fetches `./config` on startup and constructs its same-origin API client from the returned API base path. This keeps custom API and UI paths independent and avoids a build-time deployment-path assumption.

## Security

The admin module's seed contributor provides separate exact permissions for the UI entry paths,
runtime configuration, and `/assets/**`. API permissions—including `current-user`, export, and
import—remain separate, so UI delivery cannot accidentally authorize an API operation. Configured
API and UI base paths are normalized independently when the admin seed is built.

The UI entry, runtime configuration, assets, and admin API all pass through the consuming application's Spring Security authorization pipeline. There is no UI-only hardcoded role check. The framework still assigns no user to its administration roles.

The admin module contributes configurable `AUTHORIZED` resource rules for its API and UI base paths.
The consuming application assigns `AUTHZ_SYSTEM_ADMIN` only through its own seed data or an external
authority mapping; the module never assigns an administrator.

## Demo

The demo includes the UI module. Its current default profile uses the framework's generalized
OAuth2/OIDC cookie security with the bundled `employee-demo` Keycloak realm. After signing in as
`olivia`, open:

```text
/authorization-admin/
```

The SPA sends CSRF tokens on write requests, attempts a single token refresh on HTTP 401, and uses
a CSRF-protected OIDC logout form. A lightweight header-based chain exists only in integration-test
sources under the `test` profile; it is not packaged into the runnable application.

## Verification

Frontend coverage includes API query/error handling, current-user loading, capability-driven
navigation, dashboard smoke rendering, synchronization visibility, the structured permission
preview, and the destructive import warning. Demo integration coverage verifies:

- unauthenticated UI access returns 401
- an authenticated user without admin UI permission receives 403
- an authorization administrator reaches the packaged index
- the configured API/UI paths are returned at runtime
- cookie-authenticated logout rejects requests without a CSRF token

The full reactor build is the Phase 3 release gate:

```bash
./mvnw clean verify
```
