# `@isharafe/authorization-nuxt`

Source-only Nuxt 3/4 integration for the authorization framework. It provides SSR-safe permission
checks, protected routes and requests, CSRF handling, refresh recovery, and login/logout helpers.
OAuth tokens remain in Spring-managed HttpOnly cookies; Spring Security remains the enforcement
boundary.

## Configure

Reference the package from a workspace, local path, or Git dependency. A source checkout can load
the source entry point directly:

```ts
export default defineNuxtConfig({
  modules: ['@isharafe/authorization-nuxt/source'],
  authorizationNuxt: {
    backendBaseUrl: process.env.AUTHORIZATION_BACKEND_URL!,
    publicBaseUrl: process.env.NUXT_PUBLIC_BASE_URL!,
    loginEndpoint: '/oauth2/authorization/keycloak'
  }
})
```

A built package uses `@isharafe/authorization-nuxt` instead. Configure Spring with
`server.forward-headers-strategy=framework` and register OAuth login/logout callbacks on the public
Nuxt origin.

To proxy the optional Spring administration UI on the same browser origin:

```ts
authorizationNuxt: {
  backendProxyPrefixes: ['/authorization-admin']
}
```

## Control UI content

```vue
<Authorized permission="UI:EMPLOYEE_VIEW">
  <EmployeeTable />
  <template #fallback>Employees are unavailable.</template>
</Authorized>

<button v-authorization.disable="'UI:EMPLOYEE_EDIT'">Save</button>
```

Multiple permissions require all by default. Use `match="any"` or the directive object form when
one permission is enough. Programmatic checks are reactive and fail closed until permissions load:

```ts
const authorization = useAuthorization()

authorization.can('UI:EMPLOYEE_EDIT')
await authorization.refreshPermissions()
```

The default `/authorization/user/permissions` endpoint returns every enabled permission for the
current user. A control may therefore check an existing `URL:*` permission when it represents the
same backend capability, or a purpose-specific `UI:*` permission when presentation access is
separate. Browser checks remain presentation-only.

## Call protected APIs

```ts
const employee = await $authorizationFetch('/api/employees/123')
const { data, error } = await useAuthorizationFetch('/api/employees')
```

Unsafe requests obtain and send Spring's CSRF token. Concurrent 401 responses share one refresh
request, retry once, and reload the current-user permission snapshot. Failed refresh clears
permission state and raises `AuthenticationRequiredError`.

Application calls use the fixed `/api/_authorization/backend` proxy. Absolute URLs and traversal
attempts are rejected. Tokens are never returned to JavaScript.

## Protect routes

```ts
definePageMeta({
  middleware: 'authorization',
  authorization: {
    permissions: ['UI:EMPLOYEE_VIEW'],
    match: 'all'
  }
})
```

Unauthenticated browser navigation starts login, permission denial uses the configured denied
redirect, and authorization infrastructure failure produces HTTP 503 behavior.

UI checks improve presentation only. Every protected backend operation still needs a Spring
Security resource rule and permission.

## Learn more and verify

The [complete integration guide](../../docs/23-nuxt-integration.md) documents all options, endpoint
behavior, SSR/refresh details, and public errors. The
[runnable Spring + Nuxt demo](../../examples/authorization-nuxt-demo/README.md) provides an
end-to-end setup.

```bash
cd integrations/authorization-nuxt
npm install
npm test
npm run typecheck
npm run build
```
