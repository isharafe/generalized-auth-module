package com.example.authorization.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.authorization.domain.*;
import com.example.authorization.security.DefaultPermissionMatcher;
import com.example.authorization.spi.EntitlementProvider;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizationEngineTest {
  private static final ProtectedResource GET_EMPLOYEE =
      new ProtectedResource(ResourceType.URL, "GET:/api/employees/1");
  private static final AuthenticatedIdentity USER =
      new AuthenticatedIdentity("local", "alice", "alice");
  private final DefaultPermissionMatcher matcher = new DefaultPermissionMatcher();

  @Test
  void noRuleFailsClosed() {
    assertDecision(
        engine(List.of(), Set.of()),
        AuthorizationDecision.DENIED,
        AuthorizationReason.NO_MATCHING_RULE);
  }

  @Test
  void permitAllDoesNotRequireIdentity() {
    assertDecision(
        engine(List.of(rule("public", "*", "/api/**", AccessMode.PERMIT_ALL, 0)), Set.of()),
        AuthorizationDecision.GRANTED,
        AuthorizationReason.PUBLIC_RESOURCE);
  }

  @Test
  void authenticatedRequiresIdentity() {
    AuthorizationEngine engine =
        engine(List.of(rule("auth", "*", "/api/**", AccessMode.AUTHENTICATED, 0)), Set.of());
    assertThat(engine.authorize(GET_EMPLOYEE, null).decision())
        .isEqualTo(AuthorizationDecision.DENIED);
    assertThat(engine.authorize(GET_EMPLOYEE, USER).decision())
        .isEqualTo(AuthorizationDecision.GRANTED);
  }

  @Test
  void denyAllAlwaysDenies() {
    assertDecision(
        engine(List.of(rule("deny", "*", "/api/**", AccessMode.DENY_ALL, 0)), Set.of()),
        AuthorizationDecision.DENIED,
        AuthorizationReason.EXPLICIT_DENY);
  }

  @Test
  void matchingPermissionGrants() {
    Permission permission =
        new Permission(
            "VIEW", "View", null, ResourceType.URL, "GET:/api/employees/**", true);
    assertDecision(
        engine(
            List.of(rule("protected", "*", "/api/**", AccessMode.AUTHORIZED, 0)),
            Set.of(permission)),
        AuthorizationDecision.GRANTED,
        AuthorizationReason.MATCHING_PERMISSION);
  }

  @Test
  void missingPermissionDenies() {
    assertDecision(
        engine(List.of(rule("protected", "*", "/api/**", AccessMode.AUTHORIZED, 0)), Set.of()),
        AuthorizationDecision.DENIED,
        AuthorizationReason.MISSING_PERMISSION);
  }

  @Test
  void providerFailureIsIndeterminate() {
    AuthorizationEngine engine =
        new AuthorizationEngine(
            () -> {
              throw new IllegalStateException("database down");
            },
            identity -> entitlements(Set.of()),
            matcher);
    assertThat(engine.authorize(GET_EMPLOYEE, USER).decision())
        .isEqualTo(AuthorizationDecision.INDETERMINATE);
  }

  @Test
  void exactPathBeatsWildcard() {
    AuthorizationEngine engine =
        engine(
            List.of(
                rule("broad", "*", "/api/**", AccessMode.DENY_ALL, 100),
                rule("exact", "*", "/api/employees/1", AccessMode.PERMIT_ALL, 0)),
            Set.of());
    assertThat(engine.authorize(GET_EMPLOYEE, null).matchedRuleCode()).isEqualTo("exact");
  }

  @Test
  void exactPathRanksBeforeExactMethod() {
    AuthorizationEngine engine =
        engine(
            List.of(
                rule("exact-method", "GET", "/api/**", AccessMode.DENY_ALL, 100),
                rule("exact-path", "*", "/api/employees/1", AccessMode.PERMIT_ALL, 0)),
            Set.of());

    assertThat(engine.authorize(GET_EMPLOYEE, null).matchedRuleCode()).isEqualTo("exact-path");
  }

  @Test
  void exactMethodAndThenPriorityBreakTies() {
    AuthorizationEngine byMethod =
        engine(
            List.of(
                rule("wild", "*", "/api/**", AccessMode.DENY_ALL, 100),
                rule("get", "GET", "/api/**", AccessMode.PERMIT_ALL, 0)),
            Set.of());
    assertThat(byMethod.authorize(GET_EMPLOYEE, null).matchedRuleCode()).isEqualTo("get");
    AuthorizationEngine byPriority =
        engine(
            List.of(
                rule("low", "GET", "/api/**", AccessMode.DENY_ALL, 1),
                rule("high", "GET", "/api/**", AccessMode.PERMIT_ALL, 2)),
            Set.of());
    assertThat(byPriority.authorize(GET_EMPLOYEE, null).matchedRuleCode()).isEqualTo("high");
  }

  @Test
  void conflictDetectionChecksEveryRuleAtTheBestRank() {
    AuthorizationEngine engine =
        engine(
            List.of(
                rule("one", "GET", "/api/**", AccessMode.DENY_ALL, 1),
                rule("two", "GET", "/api/**", AccessMode.DENY_ALL, 1),
                rule("three", "GET", "/api/**", AccessMode.PERMIT_ALL, 1)),
            Set.of());

    assertDecision(
        engine, AuthorizationDecision.INDETERMINATE, AuthorizationReason.CONFLICTING_RULES);
  }

  @Test
  void equallyRankedConflictingRulesAreIndeterminate() {
    AuthorizationEngine engine =
        engine(
            List.of(
                rule("one", "GET", "/api/**", AccessMode.DENY_ALL, 1),
                rule("two", "GET", "/api/**", AccessMode.PERMIT_ALL, 1)),
            Set.of());
    assertDecision(
        engine, AuthorizationDecision.INDETERMINATE, AuthorizationReason.CONFLICTING_RULES);
  }

  private AuthorizationEngine engine(List<ResourceRule> rules, Set<Permission> permissions) {
    EntitlementProvider provider = identity -> entitlements(permissions);
    return new AuthorizationEngine(() -> rules, provider, matcher);
  }

  private UserEntitlements entitlements(Set<Permission> permissions) {
    return new UserEntitlements(USER, Set.of(), Set.of(), permissions, 1, Instant.now());
  }

  private ResourceRule rule(
      String code, String method, String path, AccessMode mode, int priority) {
    return new ResourceRule(code, ResourceType.URL, "%s:%s".formatted(method, path), mode, priority, true);
  }

  private void assertDecision(
      AuthorizationEngine engine, AuthorizationDecision decision, AuthorizationReason reason) {
    AuthorizationResult result =
        engine.authorize(GET_EMPLOYEE, decision == AuthorizationDecision.GRANTED ? USER : USER);
    assertThat(result.decision()).isEqualTo(decision);
    assertThat(result.reason()).isEqualTo(reason);
  }
}
