package io.github.isharafe.authorization.seed;

import io.github.isharafe.authorization.persistence.repository.SyncStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class AuthorizationSeedInitializationService {
  static final String LOCK_KEY = "GLOBAL_SEED_INITIALIZATION";

  private final SyncStateRepository syncStates;
  private final AuthorizationSeedService seeds;

  @Transactional
  public void apply(AuthorizationSeedDefinition seed) {
    syncStates
        .findByKeyForUpdate(LOCK_KEY)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Authorization seed initialization lock is missing; run authorization Flyway migrations"));
    seeds.apply(seed);
  }
}
