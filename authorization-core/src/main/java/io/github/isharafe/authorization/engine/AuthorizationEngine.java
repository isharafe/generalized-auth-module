package io.github.isharafe.authorization.engine;

import io.github.isharafe.authorization.domain.*;
import io.github.isharafe.authorization.spi.*;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AuthorizationEngine {
  private final ResourceRuleProvider rules;
  private final EntitlementProvider entitlements;
  private final PermissionMatcher matcher;

  public AuthorizationResult authorize(ProtectedResource resource, AuthenticatedIdentity identity) {
    try {
      ResourceRule rule = ResourceRuleSelector.select(rules.findEnabledRules(), resource, matcher);
      if (rule == null)
        return AuthorizationResult.of(
            AuthorizationDecision.DENIED,
            AuthorizationReason.NO_MATCHING_RULE,
            null,
            null,
            identity);
      return switch (rule.accessMode()) {
        case PERMIT_ALL ->
            AuthorizationResult.of(
                AuthorizationDecision.GRANTED,
                AuthorizationReason.PUBLIC_RESOURCE,
                rule,
                null,
                identity);
        case DENY_ALL ->
            AuthorizationResult.of(
                AuthorizationDecision.DENIED,
                AuthorizationReason.EXPLICIT_DENY,
                rule,
                null,
                identity);
        case AUTHENTICATED ->
            AuthorizationResult.of(
                identity == null ? AuthorizationDecision.DENIED : AuthorizationDecision.GRANTED,
                identity == null
                    ? AuthorizationReason.AUTHENTICATION_REQUIRED
                    : AuthorizationReason.AUTHENTICATED,
                rule,
                null,
                identity);
        case AUTHORIZED -> authorizePermission(resource, identity, rule);
      };
    } catch (AuthorizationConfigurationException ex) {
      return AuthorizationResult.of(
          AuthorizationDecision.INDETERMINATE,
          AuthorizationReason.CONFLICTING_RULES,
          null,
          null,
          identity);
    } catch (AuthorizationInfrastructureException ex) {
      return AuthorizationResult.of(
          AuthorizationDecision.INDETERMINATE,
          AuthorizationReason.PROVIDER_UNAVAILABLE,
          null,
          null,
          identity);
    }
  }

  private AuthorizationResult authorizePermission(
      ProtectedResource resource, AuthenticatedIdentity identity, ResourceRule rule) {
    if (identity == null)
      return AuthorizationResult.of(
          AuthorizationDecision.DENIED,
          AuthorizationReason.AUTHENTICATION_REQUIRED,
          rule,
          null,
          null);
    Permission permission =
        entitlements.load(identity).permissions().stream()
            .filter(candidate -> matcher.matches(candidate, resource))
            .findFirst()
            .orElse(null);
    return AuthorizationResult.of(
        permission == null ? AuthorizationDecision.DENIED : AuthorizationDecision.GRANTED,
        permission == null
            ? AuthorizationReason.MISSING_PERMISSION
            : AuthorizationReason.MATCHING_PERMISSION,
        rule,
        permission,
        identity);
  }

}
