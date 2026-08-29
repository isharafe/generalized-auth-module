package com.example.authorization.persistence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.IdentityChangeEvent;
import com.example.authorization.domain.IdentityChangeProcessingResult;
import com.example.authorization.domain.IdentityChangeType;
import com.example.authorization.domain.SynchronizationStatus;
import com.example.authorization.persistence.entity.IdentityChangeEventEntity;
import com.example.authorization.persistence.repository.IdentityChangeEventRepository;
import com.example.authorization.spi.IdentityChangeEventProcessor;
import com.example.authorization.spi.IdentitySynchronizationProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest(
    classes = {
      DatabaseIdentityChangeEventProcessorIntegrationTest.TestApplication.class,
      DatabaseIdentityChangeEventProcessorIntegrationTest.TestBeans.class
    },
    properties = {
      "spring.datasource.url=jdbc:h2:mem:identity-events;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false",
      "authorization.seed.enabled=false",
      "authorization.identity-events.processing-timeout=5m"
    })
class DatabaseIdentityChangeEventProcessorIntegrationTest {
  private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

  @jakarta.annotation.Resource private IdentityChangeEventProcessor processor;
  @jakarta.annotation.Resource private IdentityChangeEventRepository events;
  @jakarta.annotation.Resource private TrackingSynchronizationProvider synchronization;

  @BeforeEach
  void reset() {
    events.deleteAll();
    synchronization.calls = 0;
    synchronization.failure = null;
    synchronization.lastIdentity = null;
    synchronization.entered = null;
    synchronization.release = null;
  }

  @Test
  void processesAnEventOnceAndReturnsDuplicateForReplay() {
    IdentityChangeEvent event = event("evt-1", "user-1");

    assertThat(processor.process(event)).isEqualTo(IdentityChangeProcessingResult.PROCESSED);
    assertThat(processor.process(event)).isEqualTo(IdentityChangeProcessingResult.DUPLICATE);

    assertThat(synchronization.calls).isEqualTo(1);
    assertThat(synchronization.lastIdentity.subject()).isEqualTo("user-1");
    IdentityChangeEventEntity stored = stored("evt-1");
    assertThat(stored.getStatus()).isEqualTo("PROCESSED");
    assertThat(stored.getAttempts()).isEqualTo(1);
    assertThat(stored.getProcessedAt()).isEqualTo(NOW);
  }

  @Test
  void retriesAFailedDeliveryAndPersistsOnlySafeFailureDetails() {
    IdentityChangeEvent event = event("evt-retry", "user-2");
    synchronization.failure = new IllegalStateException("directory unavailable");

    assertThatThrownBy(() -> processor.process(event))
        .isInstanceOf(IdentityChangeProcessingException.class)
        .hasMessageContaining("evt-retry");

    IdentityChangeEventEntity failed = stored("evt-retry");
    assertThat(failed.getStatus()).isEqualTo("FAILED");
    assertThat(failed.getAttempts()).isEqualTo(1);
    assertThat(failed.getLastError()).isEqualTo("IllegalStateException");

    synchronization.failure = null;
    assertThat(processor.process(event)).isEqualTo(IdentityChangeProcessingResult.PROCESSED);
    assertThat(stored("evt-retry").getAttempts()).isEqualTo(2);
    assertThat(synchronization.calls).isEqualTo(2);
  }

  @Test
  void rejectsAnEventIdReusedForDifferentIdentityData() {
    processor.process(event("evt-conflict", "user-1"));

    assertThatThrownBy(() -> processor.process(event("evt-conflict", "user-2")))
        .isInstanceOf(IdentityChangeEventConflictException.class)
        .hasMessageContaining("different identity data");
    assertThat(synchronization.calls).isEqualTo(1);
  }

  @Test
  void ignoresAnActiveClaimButReclaimsAStaleDelivery() {
    IdentityChangeEvent event = event("evt-processing", "user-3");
    IdentityChangeEventEntity entity = IdentityChangeEventEntity.received(event, NOW.minusSeconds(60));
    entity.start(NOW);
    events.saveAndFlush(entity);

    assertThat(processor.process(event)).isEqualTo(IdentityChangeProcessingResult.IN_PROGRESS);
    assertThat(synchronization.calls).isZero();

    entity = stored("evt-processing");
    entity.start(NOW.minusSeconds(360));
    events.saveAndFlush(entity);

    assertThat(processor.process(event)).isEqualTo(IdentityChangeProcessingResult.PROCESSED);
    assertThat(synchronization.calls).isEqualTo(1);
    assertThat(stored("evt-processing").getAttempts()).isEqualTo(3);
  }

  @Test
  void concurrentDeliveryCannotRunTargetedSynchronizationTwice() throws Exception {
    IdentityChangeEvent event = event("evt-concurrent", "user-4");
    synchronization.entered = new CountDownLatch(1);
    synchronization.release = new CountDownLatch(1);

    try (var executor = Executors.newSingleThreadExecutor()) {
      var first = executor.submit(() -> processor.process(event));
      assertThat(synchronization.entered.await(5, TimeUnit.SECONDS)).isTrue();

      assertThat(processor.process(event))
          .isEqualTo(IdentityChangeProcessingResult.IN_PROGRESS);
      synchronization.release.countDown();

      assertThat(first.get()).isEqualTo(IdentityChangeProcessingResult.PROCESSED);
    }
    assertThat(synchronization.calls).isEqualTo(1);
  }

  private IdentityChangeEvent event(String eventId, String subject) {
    return new IdentityChangeEvent(
        eventId,
        "KEYCLOAK",
        new AuthenticatedIdentity("https://id.example/realms/company", subject, null),
        IdentityChangeType.USER_UPDATED,
        NOW.minusSeconds(10));
  }

  private IdentityChangeEventEntity stored(String eventId) {
    return events.findBySourceSystemAndEventId("KEYCLOAK", eventId).orElseThrow();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {}

  @TestConfiguration(proxyBeanMethods = false)
  static class TestBeans {
    @Bean
    Clock identityEventClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    TrackingSynchronizationProvider identitySynchronizationProvider() {
      return new TrackingSynchronizationProvider();
    }
  }

  static final class TrackingSynchronizationProvider
      implements IdentitySynchronizationProvider {
    private volatile int calls;
    private RuntimeException failure;
    private AuthenticatedIdentity lastIdentity;
    private CountDownLatch entered;
    private CountDownLatch release;

    @Override
    public boolean supported() {
      return true;
    }

    @Override
    public SynchronizationStatus status() {
      return new SynchronizationStatus(true, "test", "READY", null, null);
    }

    @Override
    public void synchronize(AuthenticatedIdentity identity) {
      calls++;
      lastIdentity = identity;
      if (entered != null) {
        entered.countDown();
        try {
          release.await();
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while holding test synchronization", exception);
        }
      }
      if (failure != null) throw failure;
    }
  }
}
