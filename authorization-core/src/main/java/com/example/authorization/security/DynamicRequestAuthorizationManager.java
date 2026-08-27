package com.example.authorization.security;

import com.example.authorization.domain.*;
import com.example.authorization.engine.AuthorizationEngine;
import com.example.authorization.spi.AuthorizationAuditPublisher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@RequiredArgsConstructor
public final class DynamicRequestAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {
  private final AuthorizationEngine engine;
  private final SpringAuthenticationIdentityResolver identityResolver;
  private final AuthorizationAuditPublisher auditPublisher;

  @Override
  public AuthorizationDecision authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    HttpServletRequest request = context.getRequest();
    AuthenticatedIdentity identity = identityResolver.resolve(authentication.get());
    ProtectedResource resource =
        new ProtectedResource(
            ResourceType.URL, "%s:%s".formatted(request.getMethod(), request.getRequestURI()));
    AuthorizationResult result = engine.authorize(resource, identity);
    auditPublisher.publishDecision(result, resource);
    if (result.decision() == com.example.authorization.domain.AuthorizationDecision.INDETERMINATE) {
      throw new IndeterminateAuthorizationException();
    }
    return new AuthorizationDecision(
        result.decision() == com.example.authorization.domain.AuthorizationDecision.GRANTED);
  }
}
