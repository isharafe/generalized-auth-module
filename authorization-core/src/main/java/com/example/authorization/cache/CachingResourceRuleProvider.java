package com.example.authorization.cache;

import com.example.authorization.domain.ResourceRule;
import com.example.authorization.spi.ResourceRuleProvider;
import com.example.authorization.spi.AuthorizationObservation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class CachingResourceRuleProvider implements ResourceRuleProvider {
  private final ResourceRuleProvider delegate;
  private final Duration ttl;
  private final AuthorizationObservation observation;
  private volatile Entry value;

  public CachingResourceRuleProvider(ResourceRuleProvider delegate, Duration ttl) {
    this(delegate, ttl, new com.example.authorization.observability.NoOpAuthorizationObservation());
  }

  public CachingResourceRuleProvider(
      ResourceRuleProvider delegate, Duration ttl, AuthorizationObservation observation) {
    this.delegate = delegate;
    this.ttl = ttl;
    this.observation = observation;
  }

  @Override
  public List<ResourceRule> findEnabledRules() {
    Entry existing = value;
    if (fresh(existing)) {
      observation.recordCacheRequest("resource-rules", true);
      return existing.rules();
    }
    observation.recordCacheRequest("resource-rules", false);
    synchronized (this) {
      existing = value;
      if (fresh(existing)) return existing.rules();
      List<ResourceRule> loaded = List.copyOf(delegate.findEnabledRules());
      value = new Entry(loaded, Instant.now());
      return loaded;
    }
  }

  public void invalidate() {
    value = null;
  }

  private boolean fresh(Entry entry) {
    return entry != null && entry.loadedAt().plus(ttl).isAfter(Instant.now());
  }

  private record Entry(List<ResourceRule> rules, Instant loadedAt) {}
}
