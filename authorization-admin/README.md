# Authorization Admin

`authorization-admin` is the optional management module for the reusable authorization framework.
It depends on `authorization-core` and packages the management REST API, transactional management
services, framework administration seed data, and the React/TypeScript single-page application in
one JAR.

## Add the module

```xml
<dependency>
  <groupId>io.github.isharafe</groupId>
  <artifactId>authorization-admin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The defaults expose the API below `/authorization-admin/api` and the UI at
`/authorization-admin/`:

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

The API can run without the UI. The UI requires the API because it loads runtime configuration and
all management data from that API. Both base paths may be changed; the module-generated permissions
and resource rules use the configured paths.

## What the module provides

- Management of users, roles, permission groups, permissions, resource rules, and external
  authority mappings.
- Effective-entitlement inspection and authorization decision testing.
- Identity-synchronization status and execution when the configured identity provider supports it.
- Decision/change audit browsing.
- URL inventory showing MVC routes, filter-chain policies, resource-rule coverage, and uncovered
  routes.
- Versioned authorization-data export and transactional replacement import.
- A same-origin administration SPA packaged as static JAR resources.
- Optimistic write versions, post-commit cache invalidation, and authorization-change audit events.

The module uses the entities, repositories, authorization engine, providers, caching, migrations,
and auditing infrastructure from `authorization-core`. It does not contain JPA entities or Flyway
migrations and does not contain Keycloak- or LDAP-specific integration.

## Module-owned seed data

The module automatically contributes its packaged seed when authorization seeding is enabled. The
seed creates its permissions, permission groups, roles, and these default `AUTHORIZED` resource
rules:

| Rule | Protected URL |
|---|---|
| `AUTHZ_ADMIN_API` | `*:/authorization-admin/api/**` |
| `AUTHZ_ADMIN_UI` | `*:/authorization-admin/**` |

Configured base paths replace the defaults in both rules. Application seed files may override a
module default by defining the same logical code. The module never assigns a user or external
identity to an administration role.

## Framework roles

The module exposes two roles. Each role contains the permission group with the same code.

| Role | Intended access |
|---|---|
| `AUTHZ_SYSTEM_VIEWER` | Load the admin UI, inspect authorization data, view synchronization and audit information, inspect URL coverage, and test decisions. It cannot change configuration, run synchronization, export data, or import data. |
| `AUTHZ_SYSTEM_ADMIN` | Every viewer capability plus all management operations, synchronization execution, data export, and replacement import. |

### Capability matrix

| Capability | `AUTHZ_SYSTEM_VIEWER` | `AUTHZ_SYSTEM_ADMIN` |
|---|:---:|:---:|
| Load the admin UI and runtime configuration | Yes | Yes |
| View current administrator and capabilities | Yes | Yes |
| View users, roles, permissions, and permission groups | Yes | Yes |
| Manage users, roles, permissions, and permission groups | No | Yes |
| View resource rules and URL inventory | Yes | Yes |
| Manage resource rules | No | Yes |
| View external authority mappings | Yes | Yes |
| Manage external authority mappings | No | Yes |
| View synchronization status | Yes | Yes |
| Run full, incremental, or targeted synchronization | No | Yes |
| View audit events | Yes | Yes |
| Test authorization decisions | Yes | Yes |
| Export authorization data | No | Yes |
| Replace authorization data from an import | No | Yes |

## Permissions

Permissions are URL permissions and therefore use the fully qualified `URL:<code>` form. Paths
shown below use the default base paths.

| Permission | HTTP pattern | Viewer | Admin |
|---|---|:---:|:---:|
| `URL:AUTHZ_ADMIN_VIEW` | `GET:/authorization-admin/api/capabilities` | Yes | Yes |
| `URL:AUTHZ_ADMIN_CURRENT_USER` | `GET:/authorization-admin/api/current-user` | Yes | Yes |
| `URL:AUTHZ_ADMIN_UI` | `GET:/authorization-admin` | Yes | Yes |
| `URL:AUTHZ_ADMIN_UI_INDEX` | `GET:/authorization-admin/` | Yes | Yes |
| `URL:AUTHZ_ADMIN_UI_CONFIG` | `GET:/authorization-admin/config` | Yes | Yes |
| `URL:AUTHZ_ADMIN_UI_ASSETS` | `GET:/authorization-admin/assets/**` | Yes | Yes |
| `URL:AUTHZ_USER_VIEW` | `GET:/authorization-admin/api/users/**` | Yes | Yes |
| `URL:AUTHZ_USER_MANAGE` | `*:/authorization-admin/api/users/**` | No | Yes |
| `URL:AUTHZ_ROLE_VIEW` | `GET:/authorization-admin/api/roles/**` | Yes | Yes |
| `URL:AUTHZ_ROLE_MANAGE` | `*:/authorization-admin/api/roles/**` | No | Yes |
| `URL:AUTHZ_PERMISSION_VIEW` | `GET:/authorization-admin/api/permissions/**` | Yes | Yes |
| `URL:AUTHZ_PERMISSION_MANAGE` | `*:/authorization-admin/api/permissions/**` | No | Yes |
| `URL:AUTHZ_PERMISSION_GROUP_VIEW` | `GET:/authorization-admin/api/permission-groups/**` | Yes | Yes |
| `URL:AUTHZ_PERMISSION_GROUP_MANAGE` | `*:/authorization-admin/api/permission-groups/**` | No | Yes |
| `URL:AUTHZ_RESOURCE_RULE_VIEW` | `GET:/authorization-admin/api/resource-rules/**` | Yes | Yes |
| `URL:AUTHZ_RESOURCE_RULE_MANAGE` | `*:/authorization-admin/api/resource-rules/**` | No | Yes |
| `URL:AUTHZ_RESOURCE_INVENTORY_VIEW` | `GET:/authorization-admin/api/resource-inventory/urls` | Yes | Yes |
| `URL:AUTHZ_EXTERNAL_MAPPING_VIEW` | `GET:/authorization-admin/api/external-mappings/**` | Yes | Yes |
| `URL:AUTHZ_EXTERNAL_MAPPING_MANAGE` | `*:/authorization-admin/api/external-mappings/**` | No | Yes |
| `URL:AUTHZ_SYNC_VIEW` | `GET:/authorization-admin/api/sync/**` | Yes | Yes |
| `URL:AUTHZ_SYNC_RUN` | `POST:/authorization-admin/api/sync/**` | No | Yes |
| `URL:AUTHZ_AUDIT_VIEW` | `GET:/authorization-admin/api/audit` | Yes | Yes |
| `URL:AUTHZ_AUTHORIZATION_TEST` | `POST:/authorization-admin/api/authorization-test` | Yes | Yes |
| `URL:AUTHZ_DATA_EXPORT` | `POST:/authorization-admin/api/data/export` | No | Yes |
| `URL:AUTHZ_DATA_IMPORT` | `POST:/authorization-admin/api/data/import` | No | Yes |

The manage permissions intentionally use `*` because their API areas contain several write methods.
The matching view permission still controls their read-only `GET` operations.

## Assign an administrator

Applications decide who receives framework roles. A database/local seed can assign a known user:

```yaml
authorization:
  seed:
    user-assignments:
      - issuer: local
        subject: admin-user
        target-type: ROLE
        target-code: AUTHZ_SYSTEM_ADMIN
        source: SEED
```

For Keycloak or LDAP, map a trusted external group or role to `AUTHZ_SYSTEM_VIEWER` or
`AUTHZ_SYSTEM_ADMIN` instead. The module deliberately contains no default user, group mapping, or
production credential.

## Build and test

The Maven lifecycle installs the pinned Node/npm versions, runs the frontend tests and production
build, packages the SPA under `META-INF/resources/authorization-admin`, and runs the Java tests:

```bash
./mvnw -pl authorization-admin -am verify
```
