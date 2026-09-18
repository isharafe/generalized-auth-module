package io.github.isharafe.authorization.engine;

import io.github.isharafe.authorization.domain.*;
import io.github.isharafe.authorization.spi.*;
import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public final class AuthorizationEngine {
  private final ResourceRuleProvider rules;
  private final EntitlementProvider entitlements;
  private final PermissionMatcher matcher;

  public AuthorizationResult authorize(ProtectedResource resource, AuthenticatedIdentity identity) {
    try {
      ResourceRule rule = selectRule(resource);
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
    } catch (RuntimeException ex) {
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

  private ResourceRule selectRule(ProtectedResource resource) {
    List<ResourceRule> matches =
        rules.findEnabledRules().stream()
            .filter(rule -> matcher.matches(rule, resource))
            .sorted(ruleComparator())
            .toList();
    if (matches.isEmpty()) return null;
    ResourceRule best = matches.getFirst();
    for (ResourceRule candidate : matches.subList(1, matches.size())) {
      if (!sameRank(best, candidate)) break;
      if (best.accessMode() != candidate.accessMode()) {
        throw new AuthorizationConfigurationException(
            "Conflicting resource rules: " + best.code() + ", " + candidate.code());
      }
    }
    return best;
  }

  private Comparator<ResourceRule> ruleComparator() {
    return (left, right) -> {
      int comparison = matcher.compareSpecificity(left, right);
      if (comparison != 0) return comparison;
      comparison = Integer.compare(right.priority(), left.priority());
      return comparison != 0 ? comparison : left.code().compareTo(right.code());
    };
  }

  private boolean sameRank(ResourceRule left, ResourceRule right) {
    return matcher.compareSpecificity(left, right) == 0 && left.priority() == right.priority();
  }
}
