package com.example.authorization.spi;

import com.example.authorization.domain.AuthorizationChangeAuditEvent;
import com.example.authorization.domain.AuthorizationResult;
import com.example.authorization.domain.ProtectedResource;

public interface AuthorizationAuditPublisher {
  void publishDecision(AuthorizationResult result, ProtectedResource resource);

  default void publishChange(AuthorizationChangeAuditEvent event) {}
}
