package com.example.authorization.cache;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.UserEntitlements;
import com.example.authorization.spi.EntitlementProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public final class CachingEntitlementProvider implements EntitlementProvider {
  private final EntitlementProvider delegate;
  private final Duration ttl;
  private final Map<String, Entry> values = new ConcurrentHashMap<>();

  @Override
  public UserEntitlements load(AuthenticatedIdentity identity) {
    Entry existing = values.get(identity.cacheKey());
    if (existing != null && existing.loadedAt().plus(ttl).isAfter(Instant.now()))
      return existing.value();
    UserEntitlements loaded = delegate.load(identity);
    values.put(identity.cacheKey(), new Entry(loaded, Instant.now()));
    return loaded;
  }

  public void invalidate(AuthenticatedIdentity identity) {
    values.remove(identity.cacheKey());
  }

  public void invalidateAll() {
    values.clear();
  }

  private record Entry(UserEntitlements value, Instant loadedAt) {}
}
