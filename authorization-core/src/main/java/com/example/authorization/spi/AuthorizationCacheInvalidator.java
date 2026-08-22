package com.example.authorization.spi;

import com.example.authorization.domain.AuthenticatedIdentity;

public interface AuthorizationCacheInvalidator {
  void invalidateIdentity(AuthenticatedIdentity identity);

  void invalidateResourceRules();

  void invalidateAll();
}
