package com.example.authorization.keycloak.sync;

import com.example.authorization.spi.IdentitySynchronizationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class KeycloakSynchronizationScheduler {
  private final IdentitySynchronizationProvider synchronization;

  @Scheduled(cron = "${authorization.keycloak.sync.full-cron:-}")
  public void full() {
    try {
      synchronization.synchronizeAll();
    } catch (RuntimeException exception) {
      log.error("Scheduled full Keycloak synchronization failed", exception);
    }
  }

  @Scheduled(cron = "${authorization.keycloak.sync.incremental-cron:-}")
  public void incremental() {
    try {
      synchronization.synchronizeIncremental();
    } catch (RuntimeException exception) {
      log.error("Scheduled incremental Keycloak synchronization failed", exception);
    }
  }
}
