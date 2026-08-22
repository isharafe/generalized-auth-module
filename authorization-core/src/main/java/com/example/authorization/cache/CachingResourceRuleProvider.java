package com.example.authorization.cache;

import com.example.authorization.domain.ResourceRule;
import com.example.authorization.spi.ResourceRuleProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public final class CachingResourceRuleProvider implements ResourceRuleProvider {
  private final ResourceRuleProvider delegate;
  private final Duration ttl;
  private volatile Entry value;

  @Override
  public List<ResourceRule> findEnabledRules() {
    Entry existing = value;
    if (existing != null && existing.loadedAt().plus(ttl).isAfter(Instant.now()))
      return existing.rules();
    List<ResourceRule> loaded = List.copyOf(delegate.findEnabledRules());
    value = new Entry(loaded, Instant.now());
    return loaded;
  }

  public void invalidate() {
    value = null;
  }

  private record Entry(List<ResourceRule> rules, Instant loadedAt) {}
}
