package com.example.authorization.spi;

import com.example.authorization.domain.AuthorizationResult;
import com.example.authorization.domain.IdentityChangeProcessingResult;
import com.example.authorization.domain.ResourceType;
import java.time.Duration;

public interface AuthorizationObservation {
  default void recordDecision(
      AuthorizationResult result, ResourceType resourceType, Duration duration) {}

  default void recordCacheRequest(String cache, boolean hit) {}

  default void recordIdentityEvent(
      IdentityChangeProcessingResult result, Duration duration) {}

  default void recordIdentityEventFailure(Duration duration) {}

  default void recordInvalidation(String scope, boolean remote) {}
}
