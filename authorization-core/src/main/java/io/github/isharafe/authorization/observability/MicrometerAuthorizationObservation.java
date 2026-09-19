package io.github.isharafe.authorization.observability;

import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.domain.IdentityChangeProcessingResult;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
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

  @Override
  public void recordPersistenceOperation(String operation, String result, Duration duration) {
    Tags tags = Tags.of("operation", operation, "result", result);
    registry.counter("authorization.persistence.operations", tags).increment();
    registry.timer("authorization.persistence.operation.duration", tags).record(duration);
  }

  @Override
  public void recordExternalRequest(
      String system, String operation, String method, String result, Duration duration) {
    Tags tags =
        Tags.of(
            "system", system,
            "operation", operation,
            "method", method,
            "result", result);
    registry.counter("authorization.external.requests", tags).increment();
    registry.timer("authorization.external.request.duration", tags).record(duration);
  }

  @Override
  public void recordExternalTokenCacheRequest(String system, boolean hit) {
    registry
        .counter(
            "authorization.external.token.cache.requests",
            "system",
            system,
            "result",
            hit ? "hit" : "miss")
        .increment();
  }

  @Override
  public void recordSynchronization(
      String source, String operation, String result, Duration duration) {
    Tags tags = Tags.of("source", source, "operation", operation, "result", result);
    registry.counter("authorization.synchronizations", tags).increment();
    registry.timer("authorization.synchronization.duration", tags).record(duration);
  }

  @Override
  public void recordLoginInitialization(
      boolean synchronizationEnabled, String result, Duration duration) {
    Tags tags =
        Tags.of(
            "synchronization", synchronizationEnabled ? "enabled" : "disabled",
            "result", result);
    registry.counter("authorization.login.initializations", tags).increment();
    registry.timer("authorization.login.initialization.duration", tags).record(duration);
  }
}
