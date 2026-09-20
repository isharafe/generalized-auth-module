# Nuxt/Nitro integration

`integrations/authorization-nuxt` is a source-only Nuxt 3/4 module. It consumes the current user's
enabled application permissions for presentation decisions and sends application requests through
a fixed same-origin Nitro proxy. The browser never reads access, refresh, or ID tokens; Spring owns
those HttpOnly cookies and remains the enforcement boundary.

## Install and configure

Reference the directory from the consuming frontend, for example with a workspace or
`file:../path/to/integrations/authorization-nuxt`. A source checkout can use the explicit source
entry point without building the module first:

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

A packaged/built copy uses the root `@isharafe/authorization-nuxt` entry point instead.

`backendBaseUrl` is a private Spring origin without a path. `publicBaseUrl` is also server-side and
must be the browser-visible Nuxt origin, such as `https://app.example.com`. Nitro derives trusted
`X-Forwarded-Host`, `X-Forwarded-Proto`, and `X-Forwarded-Port` values from it rather than trusting
incoming forwarded headers. Configure Spring with:

```yaml
server:
  forward-headers-strategy: framework

authorization:
  permissions-api:
    enabled: true
    endpoint: /authorization/user/permissions
  security:
    cookie-oauth2:
      enabled: true
      registration-id: keycloak
```

Register the OAuth login callback and post-logout redirect against the public Nuxt origin. Both
`backendBaseUrl` and `publicBaseUrl` may be supplied with Nuxt runtime environment variables
(`NUXT_AUTHORIZATION_NUXT_BACKEND_BASE_URL` and
`NUXT_AUTHORIZATION_NUXT_PUBLIC_BASE_URL`) in a deployed application.

Available module options and defaults are:

| Option | Default | Purpose |
| --- | --- | --- |
| `backendBaseUrl` | empty | Private Spring HTTP(S) origin; required at runtime |
| `publicBaseUrl` | empty | Browser-visible Nuxt origin used in forwarded headers |
| `apiProxyPrefix` | `/api/_authorization/backend` | Fixed prefix for application API calls |
| `backendProxyPrefixes` | `[]` | Additional Spring path prefixes preserved by Nitro |
| `permissionsEndpoint` | `/authorization/user/permissions` | Current-user permission snapshot |
| `csrfEndpoint` | `/authorization/security/csrf` | CSRF token initialization |
| `refreshEndpoint` | `/authorization/security/token/refresh` | Access-token refresh |
| `logoutEndpoint` | `/authorization/security/logout` | CSRF-protected logout |
| `loginEndpoint` | empty | OAuth2 login start URL |
| `deniedRedirect` | `/forbidden` | Route middleware denial destination |

The configured permission and security paths, `/oauth2/**`, and `/login/**` are proxied directly.
Application calls go through `apiProxyPrefix`; absolute URLs, backslashes, malformed escapes, and
parent traversal are rejected.

Use `backendProxyPrefixes: ['/authorization-admin']` when the optional Spring-served administration
UI must share the Nuxt origin. Each prefix registers its exact path and descendants without
stripping the prefix. Root and API-proxy-overlapping prefixes are rejected.

## Current-user permissions

Core's endpoint resolves the stable `(issuer, subject)` identity and returns every enabled
effective permission code in sorted order:

```json
{
  "permissions": ["UI:EMPLOYEE_VIEW", "URL:EMPLOYEE_EDIT"],
  "entitlementVersion": 12
}
```

The endpoint is authenticated by the default core cookie-OAuth2 chain and sends
`Cache-Control: no-store`. If the application supplies its own `SecurityFilterChain`, it must
protect this path itself. The endpoint returns 401 for no stable authenticated identity and 503
for entitlement-provider failure; infrastructure failure is never treated as an empty grant set.

UI logic can reuse an existing `URL:*` permission when a control represents that same backend
capability, or use a dedicated `UI:*` permission when presentation access has independent meaning.
Receiving URL permissions does not move enforcement into the browser; Spring Security must still
authorize every backend request.

All client checks reject non-canonical codes. They are fail-closed while state is idle, loading,
unauthenticated, or failed. Multiple permissions require all by default.

```vue
<Authorized permission="UI:EMPLOYEE_VIEW">
  <EmployeeTable />
  <template #fallback>Employee data is unavailable.</template>
</Authorized>

<Authorized
  :permissions="['UI:EMPLOYEE_EDIT', 'UI:EMPLOYEE_APPROVE']"
  match="any"
>
  <EmployeeActions />
</Authorized>

<button v-authorization.disable="'URL:EMPLOYEE_EDIT'">Save</button>
<section v-authorization="['UI:EMPLOYEE_VIEW', 'UI:EMPLOYEE_EXPORT']">
  ...
</section>
```

The directive also accepts `{ permissions: [...], match: 'any' }`. Without the `.disable`
modifier it hides denied elements; with the modifier it sets `disabled` and `aria-disabled`.

Programmatic checks are reactive:

```ts
const authorization = useAuthorization()

authorization.can('UI:EMPLOYEE_VIEW')
authorization.canAll(['UI:EMPLOYEE_VIEW', 'URL:EMPLOYEE_EXPORT'])
authorization.canAny(['UI:EMPLOYEE_EDIT', 'UI:EMPLOYEE_APPROVE'])
await authorization.refreshPermissions()
```

## Requests, CSRF, and refresh

Use the injected fetch function or async-data composable for backend requests:

```ts
const employee = await $authorizationFetch<Employee>('/api/employees/123')

await $authorizationFetch('/api/employees/123', {
  method: 'PUT',
  body: { displayName: 'Ada Lovelace' }
})

const { data, error } = await useAuthorizationFetch<Employee[]>('/api/employees')
```

For unsafe methods, the manager initializes Spring's CSRF token lazily and uses the header name
returned by the server. A 401 starts one shared refresh request, even when several application
requests fail concurrently. Successful refresh retries each original request once and reloads one
shared current-user permission snapshot. Failed refresh clears permissions and throws
`AuthenticationRequiredError`; it never starts an unexpected global redirect. Other non-success
responses throw `AuthorizationRequestError` with `statusCode` and response `data`.
Both error classes and the public state/configuration types are exported from
`@isharafe/authorization-nuxt/runtime`.

The refresh cookie remains narrowly scoped to `/authorization/security`. An arbitrary SSR page
request therefore cannot refresh an expired access token. The module intentionally skips refresh
on the server, renders permission-controlled content fail-closed, and lets the browser recover on
hydration without broadening the refresh-cookie path.

Login and logout remain explicit:

```ts
authorization.login()
await authorization.logout()
```

`login()` navigates to `loginEndpoint`. `logout()` posts a browser form containing the CSRF token,
which supports both local completion and an OIDC provider redirect.

## Route middleware

```ts
definePageMeta({
  middleware: 'authorization',
  authorization: {
    permissions: ['UI:EMPLOYEE_VIEW'],
    match: 'all'
  }
})
```

On the client, unauthenticated access starts login, denial navigates to `deniedRedirect`, and an
authorization infrastructure failure becomes 503. SSR defers the unauthenticated redirect so
hydration gets the opportunity to use the path-scoped refresh cookie.

UI visibility is not security enforcement. Every protected operation still needs a URL resource
rule and permission enforced by Spring Security.

## Runnable example

`examples/authorization-nuxt-demo` combines the module with a Spring Boot backend, the Keycloak
identity synchronization module, and the optional packaged administration UI. It demonstrates the
HR analyst, HR manager, and authorization-administrator behaviors against the repository's existing
Keycloak realm.
See the [demo README](../examples/authorization-nuxt-demo/README.md) for the user matrix, startup
commands, and configuration walkthrough.

## Verify the package

Use a Node version supported by the chosen Nuxt release, then run:

```bash
cd integrations/authorization-nuxt
npm install
npm test
npm run typecheck
npm run build
```
