package com.example.authorization.demo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.AuthorizationDecision;
import com.example.authorization.domain.AuthorizationReason;
import com.example.authorization.domain.AuthorizationResult;
import com.example.authorization.domain.ProtectedResource;
import com.example.authorization.engine.AuthorizationEngine;
import com.example.authorization.security.IndeterminateAuthorizationException;
import com.example.authorization.security.SpringAuthenticationIdentityResolver;
import com.example.authorization.spi.AuthorizationAuditPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class KeycloakDemoUiAuthorizationTest {
  private final AuthorizationEngine engine = mock(AuthorizationEngine.class);
  private final SpringAuthenticationIdentityResolver identityResolver =
      mock(SpringAuthenticationIdentityResolver.class);
  private final AuthorizationAuditPublisher audit = mock(AuthorizationAuditPublisher.class);
  private final Authentication authentication = mock(Authentication.class);
  private final AuthenticatedIdentity identity =
      new AuthenticatedIdentity("https://id.example/realms/demo", "user-1", "alice");

  @Test
  void deniedDirectPageAccessThrowsAccessDenied() {
    when(identityResolver.resolve(authentication)).thenReturn(identity);
    when(engine.authorize(any(), any()))
        .thenReturn(
            new AuthorizationResult(
                AuthorizationDecision.DENIED,
                AuthorizationReason.MISSING_PERMISSION,
                "DEMO_UI_PAGE_2",
                null,
                identity.cacheKey()));

    assertThatThrownBy(() -> authorization().require(authentication, "seePage2"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("UI:seePage2");

    verify(audit).publishDecision(any(), any(ProtectedResource.class));
  }

  @Test
  void indeterminateNavigationDecisionFailsClosed() {
    when(identityResolver.resolve(authentication)).thenReturn(identity);
    when(engine.authorize(any(), any()))
        .thenReturn(
            new AuthorizationResult(
                AuthorizationDecision.INDETERMINATE,
                AuthorizationReason.PROVIDER_UNAVAILABLE,
                null,
                null,
                identity.cacheKey()));

    assertThatThrownBy(() -> authorization().isGranted(authentication, "seePage1"))
        .isInstanceOf(IndeterminateAuthorizationException.class);
  }

  private KeycloakDemoUiAuthorization authorization() {
    return new KeycloakDemoUiAuthorization(engine, identityResolver, audit);
  }
}
