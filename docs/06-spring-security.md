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

The method prefix is normalized to uppercase. Do not include the query string.

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
