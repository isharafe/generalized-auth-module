# `@isharafe/authorization-nuxt`

Source-only Nuxt 3/4 integration for the reusable authorization framework. It keeps OAuth tokens in
HttpOnly Spring-managed cookies while providing SSR-safe UI permission checks, CSRF-protected API
requests, refresh-token recovery, login/logout helpers, and permission-aware route middleware.

## Configure

Add the package through a local or Git dependency and register it:

```ts
export default defineNuxtConfig({
  modules: ['@isharafe/authorization-nuxt'],
  authorizationNuxt: {
    backendBaseUrl: process.env.AUTHORIZATION_BACKEND_URL!,
    publicBaseUrl: process.env.NUXT_PUBLIC_BASE_URL!,
    loginEndpoint: '/oauth2/authorization/keycloak'
  }
})
```

`backendBaseUrl` is the private Spring origin. `publicBaseUrl` is the browser-visible Nuxt origin
used for trusted forwarded headers. Configure Spring with `server.forward-headers-strategy=framework`
and register OAuth login/logout redirect URIs on the public Nuxt origin.

The module exposes Spring's `/oauth2/**`, `/login/**`, `/authorization/security/**`, and
`/authorization/ui/permissions` paths through Nitro. Application backend calls use the fixed
`/api/_authorization/backend` proxy; absolute URLs and traversal are rejected.

Spring exposes the UI permission endpoint by default. It can be changed or disabled with:

```yaml
authorization:
  ui-api:
    enabled: true
    endpoint: /authorization/ui/permissions
```

## Control UI content

```vue
<Authorized permission="UI:EMPLOYEE_VIEW">
  <EmployeeTable />
  <template #fallback>Employees are unavailable.</template>
</Authorized>

<button v-authorization.disable="'UI:EMPLOYEE_EDIT'">Save</button>
<section v-authorization="['UI:EMPLOYEE_VIEW', 'UI:EMPLOYEE_EXPORT']">...</section>
```

Multiple permissions require all permissions by default. Use the object directive form
`{ permissions: [...], match: 'any' }` or the component's `match="any"` prop when appropriate.

Programmatic checks are reactive and fail closed until permissions have loaded:

```ts
const authorization = useAuthorization()

authorization.can('UI:EMPLOYEE_EDIT')
await authorization.refreshPermissions()
```

## Protected requests

```ts
const employee = await $authorizationFetch('/api/employees/123')

const { data, error } = await useAuthorizationFetch('/api/employees')
```

Unsafe requests lazily initialize CSRF and include Spring's returned CSRF header. A `401` starts one
shared refresh request, retries each rejected request once, then refreshes the UI permission snapshot.
Failed refresh clears permission state and throws `AuthenticationRequiredError`; it does not force a
global redirect. Import the public error and state types from
`@isharafe/authorization-nuxt/runtime` when application code needs to distinguish failures.

Tokens are never returned to or read by JavaScript. Logout uses a CSRF-protected browser form so both
local and OIDC redirects work:

```ts
await authorization.logout()
```

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

Unauthenticated protected routes navigate to `loginEndpoint` in the browser. Permission denials go
to `deniedRedirect`. Infrastructure failures produce HTTP 503 behavior.

The refresh cookie remains scoped to `/authorization/security` by default. Therefore an expired
access token cannot be refreshed during an unrelated SSR page request; SSR remains fail closed and
the client recovers during hydration. The module deliberately performs refresh only in the browser
so the refresh token can retain this narrow cookie path.

UI hiding and disabling are presentation behavior only. Every protected backend operation must
still be authorized by Spring Security.
