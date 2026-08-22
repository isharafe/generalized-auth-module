package com.example.authorization.domain;

public record AuthorizationResult(
    AuthorizationDecision decision,
    AuthorizationReason reason,
    String matchedRuleCode,
    String matchedPermissionCode,
    String identityKey) {
  public static AuthorizationResult of(
      AuthorizationDecision decision,
      AuthorizationReason reason,
      ResourceRule rule,
      Permission permission,
      AuthenticatedIdentity identity) {
    return new AuthorizationResult(
        decision,
        reason,
        rule == null ? null : rule.code(),
        permission == null ? null : permission.code(),
        identity == null ? null : identity.cacheKey());
  }
}
