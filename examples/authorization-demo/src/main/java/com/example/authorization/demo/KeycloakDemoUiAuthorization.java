package com.example.authorization.demo;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.AuthorizationDecision;
import com.example.authorization.domain.AuthorizationResult;
import com.example.authorization.domain.ProtectedResource;
import com.example.authorization.domain.ResourceType;
import com.example.authorization.engine.AuthorizationEngine;
import com.example.authorization.security.IndeterminateAuthorizationException;
import com.example.authorization.security.SpringAuthenticationIdentityResolver;
import com.example.authorization.spi.AuthorizationAuditPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@Profile("keycloak-demo")
public final class KeycloakDemoUiAuthorization {
  private final AuthorizationEngine engine;
  private final SpringAuthenticationIdentityResolver identityResolver;
  private final AuthorizationAuditPublisher audit;

  public KeycloakDemoUiAuthorization(
      AuthorizationEngine engine,
      SpringAuthenticationIdentityResolver identityResolver,
      AuthorizationAuditPublisher audit) {
    this.engine = engine;
    this.identityResolver = identityResolver;
    this.audit = audit;
  }

  public boolean isGranted(Authentication authentication, String resourceName) {
    AuthorizationResult result = evaluate(authentication, resourceName);
    ensureDeterminate(result);
    return result.decision() == AuthorizationDecision.GRANTED;
  }

  public void require(Authentication authentication, String resourceName) {
    AuthorizationResult result = evaluate(authentication, resourceName);
    ensureDeterminate(result);
    if (result.decision() != AuthorizationDecision.GRANTED)
      throw new AccessDeniedException("Missing UI permission UI:" + resourceName);
  }

  private void ensureDeterminate(AuthorizationResult result) {
    if (result.decision() == AuthorizationDecision.INDETERMINATE)
      throw new IndeterminateAuthorizationException();
  }

  private AuthorizationResult evaluate(Authentication authentication, String resourceName) {
    AuthenticatedIdentity identity = identityResolver.resolve(authentication);
    ProtectedResource resource = new ProtectedResource(ResourceType.UI, resourceName);
    AuthorizationResult result = engine.authorize(resource, identity);
    audit.publishDecision(result, resource);
    return result;
  }
}
