# Runtime Architecture

## DB-only flow

```text
HTTP Request
    |
Spring Security Authentication
    |
AuthenticatedIdentity
    |
DynamicAuthorizationManager
    |
ResourceRuleProvider/cache
    |
AuthorizationEngine
    |
EntitlementProvider/cache
    |
Database
```

## Decision algorithm

1. Normalize request into a `ProtectedResource`.
2. Find the most-specific matching `ResourceRule`.
3. If none: DENY by default.
4. `PERMIT_ALL`: grant.
5. `DENY_ALL`: deny.
6. `AUTHENTICATED`: require authenticated identity.
7. `AUTHORIZED`: require authentication and load entitlements.
8. Evaluate all effective permissions.
9. At least one matching permission: grant.
10. Otherwise: deny.

## Decisions

```text
GRANTED
DENIED
INDETERMINATE
```

`INDETERMINATE` means a safe decision cannot be made due to infrastructure/configuration failure.

Default HTTP mapping:

```text
401 authentication required
403 authenticated but denied
503 indeterminate
```

## Rule specificity

When multiple rules match:

1. exact path beats wildcard
2. more-specific path beats broader path
3. exact HTTP method beats `*`
4. configured priority is a secondary tie-breaker
5. equal specificity + equal priority + conflicting modes = configuration error

## Resource-type matching

`DefaultPermissionMatcher` delegates matching, validation, and specificity comparison to a
`ResourcePatternMatcher` registry keyed by `ResourceType`. The built-in strategies cover URL and UI,
and an application can supply a strategy bean to override the behavior for a type.

### URL matching

Represent each HTTP request, permission, and resource rule as one `METHOD:/path` string. Parse only
the first colon so colons in the URL path remain intact. Rule ranking evaluates the extracted method
and path independently. Use Spring path matching for the path portion.

```text
GET:/api/employees/*   -> one path segment
GET:/api/employees/**  -> descendants at any depth
```

Ignore query strings. Do not double-decode URL paths.

## Explainable result

Internal result should include safe diagnostic information:

```text
decision
reason
matchedResourceRuleCode
matchedPermissionCode
identityKey
```
