package com.example.authorization.cache;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.spi.AuthorizationCacheInvalidator;
import com.example.authorization.spi.EntitlementProvider;
import com.example.authorization.spi.ResourceRuleProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;

@RequiredArgsConstructor
public final class DefaultAuthorizationCacheInvalidator implements AuthorizationCacheInvalidator {
  private final ObjectProvider<EntitlementProvider> entitlements;
  private final ObjectProvider<ResourceRuleProvider> rules;

  @Override
  public void invalidateIdentity(AuthenticatedIdentity identity) {
    entitlements.ifAvailable(
        value -> {
          if (value instanceof CachingEntitlementProvider caching) caching.invalidate(identity);
        });
  }

  @Override
  public void invalidateResourceRules() {
    rules.ifAvailable(
        value -> {
          if (value instanceof CachingResourceRuleProvider caching) caching.invalidate();
        });
  }

  @Override
  public void invalidateAll() {
    entitlements.ifAvailable(
        value -> {
          if (value instanceof CachingEntitlementProvider caching) caching.invalidateAll();
        });
    invalidateResourceRules();
  }
}
