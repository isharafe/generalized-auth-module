package com.example.authorization.cache;

import com.example.authorization.domain.AuthorizationCacheInvalidation;
import com.example.authorization.spi.AuthorizationInvalidationPublisher;

public final class NoOpAuthorizationInvalidationPublisher
    implements AuthorizationInvalidationPublisher {
  @Override
  public void publish(AuthorizationCacheInvalidation invalidation) {}
}
