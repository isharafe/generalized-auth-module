package io.github.isharafe.authorization.cache;

import static org.mockito.ArgumentMatchers.argThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.observability.MicrometerAuthorizationObservation;
import io.github.isharafe.authorization.observability.NoOpAuthorizationObservation;
import io.github.isharafe.authorization.persistence.repository.CacheInvalidationRepository;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import io.github.isharafe.authorization.spi.AuthorizationInvalidationPublisher;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = DatabaseAuthorizationInvalidationIntegrationTest.TestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:distributed-invalidation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "authorization.seed.enabled=false",
      "authorization.distributed-invalidation.enabled=true",
      "authorization.distributed-invalidation.poll-interval=1h",
      "authorization.distributed-invalidation.instance-id=test-context"
    })
class DatabaseAuthorizationInvalidationIntegrationTest {
  @jakarta.annotation.Resource private CacheInvalidationRepository repository;
  @jakarta.annotation.Resource private AuthorizationInvalidationPublisher configuredPublisher;
  @jakarta.annotation.Resource private AuthorizationObservation configuredObservation;

  @BeforeEach
  void reset() {
    repository.deleteAll();
  }

  @Test
  void deliversAnotherInstancesInvalidationAndSkipsTheOriginEcho() {
    assertThat(configuredPublisher)
        .isInstanceOf(DatabaseAuthorizationInvalidationPublisher.class);
    assertThat(configuredObservation)
        .isInstanceOf(MicrometerAuthorizationObservation.class);
    AuthorizationProperties properties = new AuthorizationProperties();
    AuthorizationCacheInvalidator localA = mock(AuthorizationCacheInvalidator.class);
    AuthorizationCacheInvalidator localB = mock(AuthorizationCacheInvalidator.class);
    DatabaseAuthorizationInvalidationPublisher transport =
        new DatabaseAuthorizationInvalidationPublisher(repository);
    PublishingAuthorizationCacheInvalidator instanceA =
        invalidator("instance-a", localA, transport);
    PublishingAuthorizationCacheInvalidator instanceB =
        invalidator("instance-b", localB, transport);
    DatabaseAuthorizationInvalidationReceiver receiverA =
        new DatabaseAuthorizationInvalidationReceiver(
            repository, instanceA, properties, Clock.systemUTC());
    DatabaseAuthorizationInvalidationReceiver receiverB =
        new DatabaseAuthorizationInvalidationReceiver(
            repository, instanceB, properties, Clock.systemUTC());
    AuthenticatedIdentity identity = new AuthenticatedIdentity("issuer", "subject", null);

    instanceA.invalidateIdentity(identity);
    receiverA.poll();
    receiverB.poll();

    verify(localA).invalidateIdentity(identity);
    verify(localA, never())
        .invalidateIdentity(
            argThat(
                value ->
                    value != identity
                        && value.issuer().equals("issuer")
                        && value.subject().equals("subject")));
    verify(localB)
        .invalidateIdentity(
            argThat(
                value ->
                    value.issuer().equals("issuer") && value.subject().equals("subject")));
  }

  private PublishingAuthorizationCacheInvalidator invalidator(
      String origin,
      AuthorizationCacheInvalidator local,
      DatabaseAuthorizationInvalidationPublisher publisher) {
    return new PublishingAuthorizationCacheInvalidator(
        local,
        publisher,
        new NoOpAuthorizationObservation(),
        origin,
        Clock.systemUTC());
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {
    @org.springframework.context.annotation.Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
