# Phase 3 Implementation

## Delivered

Phase 3 provides the optional `authorization-admin-ui` module as a functional React and TypeScript single-page application. It depends on `authorization-core` but contains no persistence or identity-provider logic.

The SPA includes:

- a source-aware dashboard with counts and recent audit activity
- user identity details, assignment-source badges, MANUAL assignment changes, and effective permissions
- CRUD screens for roles, permission groups, permissions, resource rules, and external mappings
- structured URL permission editing with a `METHOD:/path` preview
- authorization test/explain results
- paginated, filterable audit events
- synchronization status and actions only when the capabilities endpoint reports support

Provider-specific navigation is capability-driven. Synchronized and seeded user assignments are displayed as read-only; only MANUAL assignments can be removed through the UI.

## Build and packaging

The module pins Node and npm through `frontend-maven-plugin`, uses `npm ci` from `package-lock.json`, runs Vitest during Maven's test phase, and builds the Vite production bundle during resource generation. Maven packages the result under:

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

The framework seed contributes `AUTHZ_ADMIN_UI` with `GET:/authorization-admin/**` by default and includes it in the framework viewer and admin permission groups. Configured API and UI base paths are normalized independently when the framework seed is built.

The UI entry, runtime configuration, assets, and admin API all pass through the consuming application's Spring Security authorization pipeline. There is no UI-only hardcoded role check. The framework still assigns no user to its administration roles.

A consuming application must define the applicable UI resource rule. The demo supplies an `AUTHORIZED` rule for `*:/authorization-admin/**` and assigns `AUTHZ_SYSTEM_ADMIN` only through seed data.

## Demo

The demo includes the UI module. For browser use, the demo-only authentication filter accepts:

```text
/authorization-admin?demo-user=manager
```

It authenticates the current request, sets a one-hour HttpOnly `DEMO_USER` cookie, and redirects to the clean trailing-slash URL. Header authentication remains supported for automated tests and curl. Neither demo mechanism is suitable for production.

## Verification

Frontend coverage includes API query/error handling, capability-driven navigation, dashboard smoke rendering, synchronization visibility, and the structured permission preview. Demo integration coverage verifies:

- unauthenticated UI access returns 401
- an authenticated user without admin UI permission receives 403
- an authorized manager reaches the packaged index
- the configured API/UI paths are returned at runtime
- the demo browser cookie bootstrap loads the protected SPA

The full reactor build is the Phase 3 release gate:

```bash
mvn clean verify
```
