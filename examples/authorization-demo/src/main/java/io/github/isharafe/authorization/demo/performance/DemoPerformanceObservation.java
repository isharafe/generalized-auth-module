package io.github.isharafe.authorization.demo.performance;

import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.domain.IdentityChangeProcessingResult;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import java.time.Duration;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class DemoPerformanceObservation implements AuthorizationObservation {
  private final AuthorizationObservation delegate;
  private final DemoPerformanceMeasurements measurements;

  @Override
  public void recordDecision(
      AuthorizationResult result, ResourceType resourceType, Duration duration) {
    delegate.recordDecision(result, resourceType, duration);
  }

  @Override
  public void recordCacheRequest(String cache, boolean hit) {
    delegate.recordCacheRequest(cache, hit);
  }

  @Override
  public void recordIdentityEvent(
      IdentityChangeProcessingResult result, Duration duration) {
    delegate.recordIdentityEvent(result, duration);
  }

  @Override
  public void recordIdentityEventFailure(Duration duration) {
    delegate.recordIdentityEventFailure(duration);
  }

  @Override
  public void recordInvalidation(String scope, boolean remote) {
    delegate.recordInvalidation(scope, remote);
  }

  @Override
  public void recordPersistenceOperation(String operation, String result, Duration duration) {
    delegate.recordPersistenceOperation(operation, result, duration);
  }

  @Override
  public void recordExternalRequest(
      String system, String operation, String method, String result, Duration duration) {
    delegate.recordExternalRequest(system, operation, method, result, duration);
    measurements.recordExternal(system, operation, duration);
  }

  @Override
  public void recordExternalTokenCacheRequest(String system, boolean hit) {
    delegate.recordExternalTokenCacheRequest(system, hit);
  }

  @Override
  public void recordSynchronization(
      String source, String operation, String result, Duration duration) {
    delegate.recordSynchronization(source, operation, result, duration);
  }

  @Override
  public void recordLoginInitialization(
      boolean synchronizationEnabled, String result, Duration duration) {
    delegate.recordLoginInitialization(synchronizationEnabled, result, duration);
  }
}
