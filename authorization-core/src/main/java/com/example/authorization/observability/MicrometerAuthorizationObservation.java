package com.example.authorization.observability;

import com.example.authorization.domain.AuthorizationResult;
import com.example.authorization.domain.IdentityChangeProcessingResult;
import com.example.authorization.domain.ResourceType;
import com.example.authorization.spi.AuthorizationObservation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class MicrometerAuthorizationObservation implements AuthorizationObservation {
  private final MeterRegistry registry;

  @Override
  public void recordDecision(
      AuthorizationResult result, ResourceType resourceType, Duration duration) {
    Tags tags =
        Tags.of(
            "decision", result.decision().name(),
            "reason", result.reason().name(),
            "resource.type", resourceType.name());
    registry.counter("authorization.decisions", tags).increment();
    registry.timer("authorization.decision.duration", tags).record(duration);
  }

  @Override
  public void recordCacheRequest(String cache, boolean hit) {
    registry
        .counter(
            "authorization.cache.requests",
            "cache",
            cache,
            "result",
            hit ? "hit" : "miss")
        .increment();
  }

  @Override
  public void recordIdentityEvent(
      IdentityChangeProcessingResult result, Duration duration) {
    registry.counter("authorization.identity.events", "result", result.name()).increment();
    registry.timer("authorization.identity.event.duration", "result", result.name()).record(duration);
  }

  @Override
  public void recordIdentityEventFailure(Duration duration) {
    registry.counter("authorization.identity.events", "result", "FAILED").increment();
    registry.timer("authorization.identity.event.duration", "result", "FAILED").record(duration);
  }

  @Override
  public void recordInvalidation(String scope, boolean remote) {
    registry
        .counter(
            "authorization.cache.invalidations",
            "scope",
            scope,
            "delivery",
            remote ? "remote" : "local")
        .increment();
  }
}
