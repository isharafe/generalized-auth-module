package com.example.authorization.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.authorization.domain.AuthorizationDecision;
import com.example.authorization.domain.AuthorizationReason;
import com.example.authorization.domain.AuthorizationResult;
import com.example.authorization.domain.IdentityChangeProcessingResult;
import com.example.authorization.domain.ResourceType;
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
  }
}
