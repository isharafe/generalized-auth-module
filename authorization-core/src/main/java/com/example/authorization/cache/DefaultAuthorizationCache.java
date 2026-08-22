package com.example.authorization.cache;

import com.example.authorization.domain.*;
import com.example.authorization.spi.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public final class DefaultAuthorizationCache
    implements EntitlementProvider, ResourceRuleProvider, AuthorizationCacheInvalidator {
  private final EntitlementProvider entitlementDelegate;
  private final ResourceRuleProvider ruleDelegate;
  private final Duration entitlementTtl;
  private final Duration ruleTtl;
  private final Map<String, Timed<UserEntitlements>> entitlements = new ConcurrentHashMap<>();
  private volatile Timed<List<ResourceRule>> rules;

  @Override
  public UserEntitlements load(AuthenticatedIdentity identity) {
    Timed<UserEntitlements> cached = entitlements.get(identity.cacheKey());
    if (cached != null && cached.fresh(entitlementTtl)) return cached.value();
    UserEntitlements loaded = entitlementDelegate.load(identity);
    entitlements.put(identity.cacheKey(), new Timed<>(loaded, Instant.now()));
    return loaded;
  }

  @Override
  public List<ResourceRule> findEnabledRules() {
    Timed<List<ResourceRule>> cached = rules;
    if (cached != null && cached.fresh(ruleTtl)) return cached.value();
    List<ResourceRule> loaded = List.copyOf(ruleDelegate.findEnabledRules());
    rules = new Timed<>(loaded, Instant.now());
    return loaded;
  }

  @Override
  public void invalidateIdentity(AuthenticatedIdentity identity) {
    entitlements.remove(identity.cacheKey());
  }

  @Override
  public void invalidateResourceRules() {
    rules = null;
  }

  @Override
  public void invalidateAll() {
    entitlements.clear();
    rules = null;
  }

  private record Timed<T>(T value, Instant loadedAt) {
    boolean fresh(Duration ttl) {
      return loadedAt.plus(ttl).isAfter(Instant.now());
    }
  }
}
