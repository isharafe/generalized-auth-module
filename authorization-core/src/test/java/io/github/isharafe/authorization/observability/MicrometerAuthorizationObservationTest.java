package io.github.isharafe.authorization.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.isharafe.authorization.domain.AuthorizationDecision;
import io.github.isharafe.authorization.domain.AuthorizationReason;
import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.domain.IdentityChangeProcessingResult;
import io.github.isharafe.authorization.domain.ResourceType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MicrometerAuthorizationObservationTest {
  @Test
  void recordsLowCardinalityDecisionCacheEventAndInvalidationMetrics() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    MicrometerAuthorizationObservation observation =
        new MicrometerAuthorizationObservation(registry);

    observation.recordDecision(
        AuthorizationResult.of(
            AuthorizationDecision.DENIED,
            AuthorizationReason.NO_MATCHING_RULE,
            null,
            null,
            null),
        ResourceType.URL,
        Duration.ofMillis(2));
    observation.recordCacheRequest("entitlements", true);
    observation.recordIdentityEvent(IdentityChangeProcessingResult.DUPLICATE, Duration.ofMillis(1));
    observation.recordIdentityEventFailure(Duration.ofMillis(3));
    observation.recordInvalidation("identity", true);
    observation.recordPersistenceOperation(
        "entitlements_load", "success", Duration.ofMillis(4));
    observation.recordExternalRequest(
        "keycloak", "groups", "GET", "success", Duration.ofMillis(5));
    observation.recordExternalTokenCacheRequest("keycloak", true);
    observation.recordSynchronization(
        "keycloak", "targeted", "success", Duration.ofMillis(6));
    observation.recordLoginInitialization(true, "success", Duration.ofMillis(7));

    assertThat(
            registry
                .get("authorization.decisions")
                .tags("decision", "DENIED", "reason", "NO_MATCHING_RULE", "resource.type", "URL")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("authorization.cache.requests")
                .tags("cache", "entitlements", "result", "hit")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("authorization.identity.events")
                .tag("result", "DUPLICATE")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("authorization.identity.events")
                .tag("result", "FAILED")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("authorization.cache.invalidations")
                .tags("scope", "identity", "delivery", "remote")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("authorization.persistence.operations")
                .tags("operation", "entitlements_load", "result", "success")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("authorization.external.requests")
                .tags(
                    "system", "keycloak",
                    "operation", "groups",
                    "method", "GET",
                    "result", "success")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("authorization.external.token.cache.requests")
                .tags("system", "keycloak", "result", "hit")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("authorization.synchronizations")
                .tags("source", "keycloak", "operation", "targeted", "result", "success")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("authorization.login.initializations")
                .tags("synchronization", "enabled", "result", "success")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("authorization.external.request.duration")
                .tags(
                    "system", "keycloak",
                    "operation", "groups",
                    "method", "GET",
                    "result", "success")
                .timer()
                .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
        .isEqualTo(5);
  }
}
