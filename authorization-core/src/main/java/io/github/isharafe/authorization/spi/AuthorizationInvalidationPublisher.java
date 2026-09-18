package io.github.isharafe.authorization.spi;

import io.github.isharafe.authorization.domain.AuthorizationCacheInvalidation;

public interface AuthorizationInvalidationPublisher {
  void publish(AuthorizationCacheInvalidation invalidation);
}
