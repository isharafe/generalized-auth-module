package com.example.authorization.security;

import com.example.authorization.domain.*;
import com.example.authorization.engine.AuthorizationEngine;
import com.example.authorization.spi.AuthorizationAuditPublisher;
import com.example.authorization.spi.AuthorizationObservation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

public final class DynamicRequestAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {
  private final AuthorizationEngine engine;
  private final SpringAuthenticationIdentityResolver identityResolver;
  private final AuthorizationAuditPublisher auditPublisher;
  private final AuthorizationObservation observation;

  public DynamicRequestAuthorizationManager(
      AuthorizationEngine engine,
      SpringAuthenticationIdentityResolver identityResolver,
      AuthorizationAuditPublisher auditPublisher) {
    this(
        engine,
        identityResolver,
        auditPublisher,
        new com.example.authorization.observability.NoOpAuthorizationObservation());
  }

  public DynamicRequestAuthorizationManager(
      AuthorizationEngine engine,
      SpringAuthenticationIdentityResolver identityResolver,
      AuthorizationAuditPublisher auditPublisher,
      AuthorizationObservation observation) {
    this.engine = engine;
    this.identityResolver = identityResolver;
    this.auditPublisher = auditPublisher;
    this.observation = observation;
  }

  @Override
  public AuthorizationDecision authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    HttpServletRequest request = context.getRequest();
    AuthenticatedIdentity identity = identityResolver.resolve(authentication.get());
    String path = applicationPath(request);
    ProtectedResource resource =
        new ProtectedResource(
            ResourceType.URL, "%s:%s".formatted(request.getMethod(), path));
    long started = System.nanoTime();
    AuthorizationResult result = engine.authorize(resource, identity);
    observation.recordDecision(
        result, resource.resourceType(), Duration.ofNanos(System.nanoTime() - started));
    auditPublisher.publishDecision(result, resource);
    if (result.decision() == com.example.authorization.domain.AuthorizationDecision.INDETERMINATE) {
      throw new IndeterminateAuthorizationException();
    }
    return new AuthorizationDecision(
        result.decision() == com.example.authorization.domain.AuthorizationDecision.GRANTED);
  }

  private String applicationPath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
      String relative = uri.substring(contextPath.length());
      return relative.isEmpty() ? "/" : relative;
    }
    return uri;
  }
}
