package io.github.isharafe.authorization.spi;

import io.github.isharafe.authorization.domain.AuthorizationChangeAuditEvent;
import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.domain.ProtectedResource;

public interface AuthorizationAuditPublisher {
  void publishDecision(AuthorizationResult result, ProtectedResource resource);

  default void publishChange(AuthorizationChangeAuditEvent event) {}
}
