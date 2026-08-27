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

The consuming application owns authentication configuration.

Possible mechanisms:

- OAuth2 Resource Server JWT
- session authentication
- custom `AuthenticationProvider`
- pre-authentication/gateway
- demo-only local test auth

The authorization framework consumes the resulting Spring `Authentication`.

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

## SecurityFilterChain developer experience

Do not aggressively replace an application's security configuration.

Expose a bean/configurer and document the recommended wiring. Conditional auto-configuration may help when safe.

The demo app must show the supported integration exactly.

## Demo browser UI authorization

The `keycloak-demo` profile combines OAuth2/OIDC login with the normal URL authorization manager.
After login it synchronizes the authenticated `(issuer, subject)`, then evaluates opaque UI
resources through `AuthorizationEngine`:

```text
UI:seePage1
UI:seePage2
```

The `/demo-ui/**` URL rule requires authentication. Separately, the landing page renders only links
whose UI decisions are granted, and each page endpoint repeats its UI decision to prevent direct URL
navigation from bypassing the presentation check. UI decision results use the same audit and
indeterminate/503 behavior as URL decisions.
