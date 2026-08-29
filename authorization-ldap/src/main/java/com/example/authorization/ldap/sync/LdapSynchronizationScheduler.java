package com.example.authorization.ldap.sync;

import com.example.authorization.spi.IdentitySynchronizationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class LdapSynchronizationScheduler {
  private final IdentitySynchronizationProvider synchronization;

  @Scheduled(cron = "${authorization.ldap.sync.full-cron:-}")
  public void full() {
    try {
      synchronization.synchronizeAll();
    } catch (RuntimeException exception) {
      log.error("Scheduled full LDAP synchronization failed", exception);
    }
  }

  @Scheduled(cron = "${authorization.ldap.sync.incremental-cron:-}")
  public void incremental() {
    try {
      synchronization.synchronizeIncremental();
    } catch (RuntimeException exception) {
      log.error("Scheduled incremental LDAP synchronization failed", exception);
    }
  }
}
