package io.github.isharafe.authorization.cache;

import io.github.isharafe.authorization.domain.AuthorizationCacheInvalidation;
import io.github.isharafe.authorization.spi.AuthorizationInvalidationPublisher;

public final class NoOpAuthorizationInvalidationPublisher
    implements AuthorizationInvalidationPublisher {
  @Override
  public void publish(AuthorizationCacheInvalidation invalidation) {}
}
