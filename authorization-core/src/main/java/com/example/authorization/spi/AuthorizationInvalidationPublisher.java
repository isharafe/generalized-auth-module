package com.example.authorization.spi;

import com.example.authorization.domain.AuthorizationCacheInvalidation;

public interface AuthorizationInvalidationPublisher {
  void publish(AuthorizationCacheInvalidation invalidation);
}
