package io.github.isharafe.authorization.cache;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import io.github.isharafe.authorization.persistence.entity.CacheInvalidationEntity;
import io.github.isharafe.authorization.persistence.repository.CacheInvalidationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;

public final class DatabaseAuthorizationInvalidationReceiver {
  private final CacheInvalidationRepository repository;
  private final PublishingAuthorizationCacheInvalidator invalidator;
  private final AuthorizationProperties properties;
  private final Clock clock;
  private long lastSeenId;
  private Instant lastCleanup = Instant.EPOCH;

  public DatabaseAuthorizationInvalidationReceiver(
      CacheInvalidationRepository repository,
      PublishingAuthorizationCacheInvalidator invalidator,
      AuthorizationProperties properties,
      Clock clock) {
    this.repository = repository;
    this.invalidator = invalidator;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${authorization.distributed-invalidation.poll-interval:1s}")
  public void poll() {
    int batchSize = properties.getDistributedInvalidation().getBatchSize();
    List<CacheInvalidationEntity> batch;
    do {
      batch =
          repository.findByIdGreaterThanOrderByIdAsc(
              lastSeenId, PageRequest.of(0, batchSize));
      for (CacheInvalidationEntity entity : batch) {
        if (!entity.getOrigin().equals(invalidator.origin()))
          invalidator.receive(entity.toDomain());
        lastSeenId = entity.getId();
      }
    } while (batch.size() == batchSize);
    cleanupIfDue();
  }

  private void cleanupIfDue() {
    Instant now = Instant.now(clock);
    if (lastCleanup.plusSeconds(60).isAfter(now)) return;
    repository.deleteCreatedBefore(
        now.minus(properties.getDistributedInvalidation().getRetention()));
    lastCleanup = now;
  }
}
