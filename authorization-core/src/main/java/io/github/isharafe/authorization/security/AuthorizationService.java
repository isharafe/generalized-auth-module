package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.AuthorizationDecision;
import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.domain.ProtectedResource;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.engine.AuthorizationEngine;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Application-facing facade for authorizing URL, UI, and future resource types. */
@RequiredArgsConstructor
public final class AuthorizationService {
  private final AuthorizationEngine engine;
  private final SpringAuthenticationIdentityResolver identityResolver;
  private final AuthorizationAuditPublisher auditPublisher;
  private final AuthorizationObservation observation;

  public AuthorizationResult evaluate(ResourceType resourceType, String resource) {
    return evaluate(currentAuthentication(), resourceType, resource);
  }

  public AuthorizationResult evaluate(
      Authentication authentication, ResourceType resourceType, String resource) {
    return evaluate(authentication, new ProtectedResource(resourceType, resource));
  }

  public AuthorizationResult evaluate(ProtectedResource resource) {
    return evaluate(currentAuthentication(), resource);
  }

  public AuthorizationResult evaluate(
      Authentication authentication, ProtectedResource resource) {
    AuthenticatedIdentity identity = identityResolver.resolve(authentication);
    long started = System.nanoTime();
    AuthorizationResult result = engine.authorize(resource, identity);
    observation.recordDecision(
        result, resource.resourceType(), Duration.ofNanos(System.nanoTime() - started));
    auditPublisher.publishDecision(result, resource);
    return result;
  }

  public boolean isGranted(ResourceType resourceType, String resource) {
    return isGranted(currentAuthentication(), resourceType, resource);
  }

  public boolean isGranted(
      Authentication authentication, ResourceType resourceType, String resource) {
    AuthorizationResult result = evaluate(authentication, resourceType, resource);
    ensureDeterminate(result);
    return result.decision() == AuthorizationDecision.GRANTED;
  }

  public void requireGranted(ResourceType resourceType, String resource) {
    requireGranted(currentAuthentication(), resourceType, resource);
  }

  public void requireGranted(
      Authentication authentication, ResourceType resourceType, String resource) {
    AuthorizationResult result = evaluate(authentication, resourceType, resource);
    ensureDeterminate(result);
    if (result.decision() != AuthorizationDecision.GRANTED) {
      throw new AccessDeniedException("Access denied for " + resourceType + ":" + resource);
    }
  }

  private Authentication currentAuthentication() {
    return SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
  }

  private void ensureDeterminate(AuthorizationResult result) {
    if (result.decision() == AuthorizationDecision.INDETERMINATE) {
      throw new IndeterminateAuthorizationException();
    }
  }
}
