package io.github.isharafe.authorization.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.isharafe.authorization.domain.AccessMode;
import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.AuthorizationDecision;
import io.github.isharafe.authorization.domain.Permission;
import io.github.isharafe.authorization.domain.ProtectedResource;
import io.github.isharafe.authorization.domain.ResourceRule;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.domain.UserEntitlements;
import io.github.isharafe.authorization.engine.AuthorizationEngine;
import io.github.isharafe.authorization.observability.NoOpAuthorizationObservation;
import io.github.isharafe.authorization.security.DefaultPermissionMatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachingProviderHardeningTest {
  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("local", "alice", "alice");

  @Test
  void concurrentEntitlementMissesLoadTheIdentityOnlyOnce() throws Exception {
    AtomicInteger loads = new AtomicInteger();
    CachingEntitlementProvider cache =
        new CachingEntitlementProvider(
            identity -> {
              loads.incrementAndGet();
              return entitlements();
            },
            Duration.ofMinutes(1));

    runConcurrently(32, () -> cache.load(IDENTITY));

    assertThat(loads).hasValue(1);
  }

  @Test
  void concurrentRuleMissesLoadRulesOnlyOnce() throws Exception {
    AtomicInteger loads = new AtomicInteger();
    CachingResourceRuleProvider cache =
        new CachingResourceRuleProvider(
            () -> {
              loads.incrementAndGet();
              return rules();
            },
            Duration.ofMinutes(1));

    runConcurrently(32, cache::findEnabledRules);

    assertThat(loads).hasValue(1);
  }

  @Test
  void sustainedCachedAuthorizationLoadRemainsLocalAndBounded() {
    AtomicInteger entitlementLoads = new AtomicInteger();
    AtomicInteger ruleLoads = new AtomicInteger();
    AuthorizationEngine engine =
        new AuthorizationEngine(
            new CachingResourceRuleProvider(
                () -> {
                  ruleLoads.incrementAndGet();
                  return rules();
                },
                Duration.ofMinutes(1),
                new NoOpAuthorizationObservation()),
            new CachingEntitlementProvider(
                identity -> {
                  entitlementLoads.incrementAndGet();
                  return entitlements();
                },
                Duration.ofMinutes(1),
                new NoOpAuthorizationObservation()),
            new DefaultPermissionMatcher());
    ProtectedResource resource = new ProtectedResource(ResourceType.URL, "GET:/employees/42");

    long started = System.nanoTime();
    AuthorizationDecision decision = null;
    for (int index = 0; index < 25_000; index++)
      decision = engine.authorize(resource, IDENTITY).decision();

    assertThat(decision).isEqualTo(AuthorizationDecision.GRANTED);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    assertThat(ruleLoads).hasValue(1);
    assertThat(entitlementLoads).hasValue(1);
  }

  private void runConcurrently(int workers, Runnable action) throws Exception {
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(workers)) {
      List<Future<Void>> futures = new ArrayList<>();
      for (int index = 0; index < workers; index++)
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  action.run();
                  return null;
                }));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<Void> future : futures) future.get();
    }
  }

  private List<ResourceRule> rules() {
    return List.of(
        new ResourceRule(
            "EMPLOYEES", ResourceType.URL, "*:/employees/**", AccessMode.AUTHORIZED, 0, true));
  }

  private UserEntitlements entitlements() {
    Permission permission =
        new Permission(
            "URL:EMPLOYEE_VIEW",
            "View employees",
            null,
            ResourceType.URL,
            "GET:/employees/**",
            true);
    return new UserEntitlements(
        IDENTITY, Set.of(), Set.of(), Set.of(permission), 1, Instant.now());
  }
}
