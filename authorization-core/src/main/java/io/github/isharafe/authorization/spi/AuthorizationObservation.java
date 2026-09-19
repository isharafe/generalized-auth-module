package io.github.isharafe.authorization.spi;

import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.domain.IdentityChangeProcessingResult;
import io.github.isharafe.authorization.domain.ResourceType;
import java.time.Duration;

public interface AuthorizationObservation {
  default void recordDecision(
      AuthorizationResult result, ResourceType resourceType, Duration duration) {}

  default void recordCacheRequest(String cache, boolean hit) {}

  default void recordIdentityEvent(
      IdentityChangeProcessingResult result, Duration duration) {}

  default void recordIdentityEventFailure(Duration duration) {}

  default void recordInvalidation(String scope, boolean remote) {}

  default void recordPersistenceOperation(
      String operation, String result, Duration duration) {}

  default void recordExternalRequest(
      String system, String operation, String method, String result, Duration duration) {}

  default void recordExternalTokenCacheRequest(String system, boolean hit) {}

  default void recordSynchronization(
      String source, String operation, String result, Duration duration) {}

  default void recordLoginInitialization(
      boolean synchronizationEnabled, String result, Duration duration) {}
}
