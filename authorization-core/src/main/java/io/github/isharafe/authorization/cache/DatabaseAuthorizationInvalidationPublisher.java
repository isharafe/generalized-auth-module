package io.github.isharafe.authorization.cache;

import io.github.isharafe.authorization.domain.AuthorizationCacheInvalidation;
import io.github.isharafe.authorization.persistence.entity.CacheInvalidationEntity;
import io.github.isharafe.authorization.persistence.repository.CacheInvalidationRepository;
import io.github.isharafe.authorization.spi.AuthorizationInvalidationPublisher;
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
