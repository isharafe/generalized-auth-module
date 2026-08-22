package com.example.authorization.spi;

import com.example.authorization.domain.AdminAuditEvent;
import com.example.authorization.domain.AuthorizationResult;
import com.example.authorization.domain.ProtectedResource;

public interface AuthorizationAuditPublisher {
  void publish(AuthorizationResult result, ProtectedResource resource);

  default void publish(AdminAuditEvent event) {}
}
