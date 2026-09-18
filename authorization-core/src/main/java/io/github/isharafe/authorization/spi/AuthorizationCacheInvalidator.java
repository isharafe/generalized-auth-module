package io.github.isharafe.authorization.spi;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;

public interface AuthorizationCacheInvalidator {
  void invalidateIdentity(AuthenticatedIdentity identity);

  void invalidateResourceRules();

  void invalidateAll();
}
