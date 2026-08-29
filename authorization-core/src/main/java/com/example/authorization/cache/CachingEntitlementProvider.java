package com.example.authorization.cache;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.UserEntitlements;
import com.example.authorization.spi.EntitlementProvider;
import com.example.authorization.spi.AuthorizationObservation;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CachingEntitlementProvider implements EntitlementProvider {
  private final EntitlementProvider delegate;
  private final Duration ttl;
  private final AuthorizationObservation observation;
  private final Map<String, Entry> values = new ConcurrentHashMap<>();

  public CachingEntitlementProvider(EntitlementProvider delegate, Duration ttl) {
    this(delegate, ttl, new com.example.authorization.observability.NoOpAuthorizationObservation());
  }

  public CachingEntitlementProvider(
      EntitlementProvider delegate, Duration ttl, AuthorizationObservation observation) {
    this.delegate = delegate;
    this.ttl = ttl;
    this.observation = observation;
  }

  @Override
  public UserEntitlements load(AuthenticatedIdentity identity) {
    Entry existing = values.get(identity.cacheKey());
    if (fresh(existing)) {
      observation.recordCacheRequest("entitlements", true);
      return existing.value();
    }
    observation.recordCacheRequest("entitlements", false);
    return values
        .compute(
            identity.cacheKey(),
            (key, current) ->
                fresh(current) ? current : new Entry(delegate.load(identity), Instant.now()))
        .value();
  }

  public void invalidate(AuthenticatedIdentity identity) {
    values.remove(identity.cacheKey());
  }

  public void invalidateAll() {
    values.clear();
  }

  private boolean fresh(Entry entry) {
    return entry != null && entry.loadedAt().plus(ttl).isAfter(Instant.now());
  }

  private record Entry(UserEntitlements value, Instant loadedAt) {}
}
