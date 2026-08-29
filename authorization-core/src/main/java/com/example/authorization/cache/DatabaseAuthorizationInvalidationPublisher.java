package com.example.authorization.cache;

import com.example.authorization.domain.AuthorizationCacheInvalidation;
import com.example.authorization.persistence.entity.CacheInvalidationEntity;
import com.example.authorization.persistence.repository.CacheInvalidationRepository;
import com.example.authorization.spi.AuthorizationInvalidationPublisher;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class DatabaseAuthorizationInvalidationPublisher
    implements AuthorizationInvalidationPublisher {
  private final CacheInvalidationRepository repository;

  @Override
  public void publish(AuthorizationCacheInvalidation invalidation) {
    repository.save(CacheInvalidationEntity.from(invalidation));
  }
}
