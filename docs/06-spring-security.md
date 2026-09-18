# Spring Security Integration

## Main mechanism

Use:

```java
AuthorizationManager<RequestAuthorizationContext> authorizationManager;
```

Suggested implementation:

```text
DynamicRequestAuthorizationManager
```

Do not use a Spring MVC interceptor as the primary authorization layer.

## Authentication ownership

Core supports two explicit ownership modes:

- With `authorization.security.cookie-oauth2.enabled=true`, core configures OAuth2/OIDC login,
  JWT resource-server authentication, bearer-token cookies, CSRF, refresh, and logout. Normal
  application requests are stateless. Only the authorization-code handshake uses a temporary HTTP
  session, which is invalidated after the tokens have been transferred to HttpOnly cookies.
- With cookie OAuth2 disabled, the consuming application owns authentication and must provide a
  `SecurityFilterChain`. Core fails startup when neither mode supplies a chain.

An application-defined `SecurityFilterChain` always makes both core default chains back off. This
is the full override for session authentication, a custom `AuthenticationProvider`,
pre-authentication/gateway integration, or another authentication architecture. The application
then wires `DynamicRequestAuthorizationManager` and
`AuthorizationServiceUnavailableHandler` into its chain.

For smaller changes while retaining the defaults, provide ordered
`AuthorizationHttpSecurityCustomizer` beans targeting `OAUTH2_LOGIN` or `APPLICATION`.

## Identity resolver

Provide a default adapter such as:

```java
public interface SpringAuthenticationIdentityResolver {
    AuthenticatedIdentity resolve(Authentication authentication);
}
```

Default behavior:

- JWT/OIDC: prefer `iss` + `sub`
- local supported identity: controlled issuer (e.g. `local`) + principal name
- application may override bean

## Protected resource

For HTTP, store the method and request path as one canonical permission-pattern string:

```text
resourceType = URL
pattern      = GET:/api/employees/123
```

The canonical method prefix is uppercase. Servlet request methods already use that form, and the
admin authorization-test endpoint uppercases its method input. Seed and admin CRUD patterns are
validated as canonical uppercase values rather than silently rewritten. Do not include the query
string.

Servlet context paths are removed before authorization, so rules remain application-relative (for
example, a request URI `/company/api/users` under context path `/company` is checked as
`GET:/api/users`). The container's parsed request URI is used; override headers do not change the
method being authorized.

## Matching

`DefaultPermissionMatcher` selects a `ResourcePatternMatcher` strategy by `ResourceType`. URL uses
Spring `PathPattern`/`PathPatternParser`; UI uses exact opaque-identifier matching. Applications may
supply a strategy bean to override the matcher for a resource type.

Do not implement wildcard -> regex manually.

Tests must cover:

- `/*` vs `/**`
- trailing slash behavior
- encoded paths
- repeated slash
- context path
- exact vs wildcard method
- query string ignored

The release-blocking HTTP regression suite additionally checks encoded slash/backslash attempts,
semicolon path parameters, repeated slashes, method-override headers, context-path handling, and
trailing-slash behavior through the actual Spring Security filter chain.

## Authorization flow

```text
request
 -> identity resolver
 -> resource-rule lookup
 -> access-mode check
 -> entitlement provider when needed
 -> permission matcher
 -> decision
```

## 401 / 403 / 503

```text
401: protected request requires authentication
403: valid authenticated user lacks permission / DENY_ALL
503: decision is INDETERMINATE
```

Use Spring Security entry point/access-denied integration so normal framework semantics are preserved.

## Application-facing authorization

Applications can inject core's provider-neutral `AuthorizationService` for non-request or
component-level checks. The current-user overload reads Spring Security's active authentication:

```java
if (authorization.isGranted(ResourceType.UI, "employeeDirectory")) {
  // render the component
}

authorization.requireGranted(ResourceType.UI, "employeeDirectory");
```

Overloads accepting an explicit `Authentication` are available for asynchronous code and tests.
`evaluate(...)` returns the complete three-state `AuthorizationResult`; `isGranted(...)` returns
false only for a determinate denial, and both boolean/required checks throw
`IndeterminateAuthorizationException` when infrastructure is unavailable. Every evaluation uses
the normal resource rules and permission matcher and publishes the same audit and observation data
as URL authorization.

## SecurityFilterChain developer experience

The cookie mode supplies two chains:

1. An `@Order(0)` OAuth2 login chain for `/oauth2/**` and `/login/**`, with
   `IF_REQUIRED` sessions for the authorization request only.
2. An `@Order(1)` stateless application chain that resolves bearer tokens from either the
   standard `Authorization` header or the configured access-token cookie, then delegates all
   non-framework endpoints to `DynamicRequestAuthorizationManager`.

Supplying different non-equal bearer tokens in the header and cookie is rejected as an invalid
request. HTML requests receive an OAuth2 login redirect when unauthenticated; API-style requests
receive 401. Authenticated denials remain 403 and unavailable authorization infrastructure remains
503.

The built-in endpoints are:

```text
GET  /authorization/security/csrf
POST /authorization/security/token/refresh
POST /authorization/security/logout
```

Unsafe cookie-authenticated requests require CSRF. The CSRF endpoint returns the token plus its
header and parameter names and sets the readable same-origin CSRF cookie. Access, refresh, and ID
tokens remain HttpOnly. The refresh token is scoped to `/authorization/security`, while the OIDC
ID-token hint is scoped to the logout endpoint.

The admin SPA sends the CSRF header automatically. After a 401 it permits only one refresh request
at a time, then retries each waiting request once. Refresh rotation replaces both token cookies;
refresh failure clears them and sends the browser through login again.

## Logout modes

`authorization.security.cookie-oauth2.logout.mode` selects:

- `LOCAL` (default): clear application token and CSRF cookies, optionally revoke the refresh token
  when the provider advertises a revocation endpoint, and redirect locally. The identity-provider
  SSO session remains, so a later login may be silent. This is fast and avoids signing the user out
  of sibling applications.
- `OIDC`: perform the same local cleanup and optional revocation, then use the validated ID token
  as `id_token_hint` for RP-initiated logout. This also ends the provider SSO session, which is
  appropriate for explicit “sign out everywhere” behavior but affects sibling applications and
  requires provider `end_session_endpoint` metadata.

Both modes use a CSRF-protected POST. The ID token is never exposed to JavaScript. If its logout
hint is absent or cannot be validated, OIDC mode safely completes local logout instead.

Changing the property requires no application chain. Cookie names, security flags, SameSite values,
endpoint paths, login-success URI, post-logout redirect URI, and refresh-token revocation are also
configurable.

## Demo browser UI authorization

The `keycloak-demo` profile enables core cookie OAuth2 with the normal URL authorization manager.
After login it synchronizes the authenticated `(issuer, subject)`, then evaluates opaque UI
resources through the core `AuthorizationService`:

```text
UI:employeeDirectory
UI:managerWorkspace
```

The `/demo-ui/**` URL rule requires authentication. Separately, the landing page renders only links
whose UI decisions are granted, and each page endpoint repeats its UI decision to prevent direct URL
navigation from bypassing the presentation check. UI decision results use the same audit and
indeterminate/503 behavior as URL decisions.
