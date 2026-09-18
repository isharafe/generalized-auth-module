package io.github.isharafe.authorization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.AuthorizationDecision;
import io.github.isharafe.authorization.domain.AuthorizationReason;
import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.domain.ProtectedResource;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.engine.AuthorizationEngine;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthorizationServiceTest {
  private final AuthorizationEngine engine = mock(AuthorizationEngine.class);
  private final SpringAuthenticationIdentityResolver identityResolver =
      mock(SpringAuthenticationIdentityResolver.class);
  private final AuthorizationAuditPublisher audit = mock(AuthorizationAuditPublisher.class);
  private final AuthorizationObservation observation = mock(AuthorizationObservation.class);
  private final Authentication authentication =
      new TestingAuthenticationToken("alice", null, "ROLE_USER");
  private final AuthenticatedIdentity identity =
      new AuthenticatedIdentity("https://id.example/realms/demo", "user-1", "alice");
  private final ProtectedResource resource = new ProtectedResource(ResourceType.UI, "seePage1");

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void evaluatesExplicitAuthenticationAndPublishesAuditAndObservation() {
    AuthorizationResult granted = result(AuthorizationDecision.GRANTED);
    when(identityResolver.resolve(authentication)).thenReturn(identity);
    when(engine.authorize(resource, identity)).thenReturn(granted);

    AuthorizationResult actual = service().evaluate(authentication, resource);

    assertThat(actual).isSameAs(granted);
    verify(audit).publishDecision(granted, resource);
    verify(observation)
        .recordDecision(eq(granted), eq(ResourceType.UI), any(Duration.class));
  }

  @Test
  void currentUserOverloadUsesTheSpringSecurityContext() {
    SecurityContextHolder.getContext().setAuthentication(authentication);
    when(identityResolver.resolve(authentication)).thenReturn(identity);
    when(engine.authorize(resource, identity))
        .thenReturn(result(AuthorizationDecision.GRANTED));

    assertThat(service().isGranted(ResourceType.UI, "seePage1")).isTrue();
  }

  @Test
  void deniedDecisionReturnsFalseOrThrowsForRequiredAccess() {
    when(identityResolver.resolve(authentication)).thenReturn(identity);
    when(engine.authorize(resource, identity))
        .thenReturn(result(AuthorizationDecision.DENIED));
    AuthorizationService service = service();

    assertThat(service.isGranted(authentication, ResourceType.UI, "seePage1")).isFalse();
    assertThatThrownBy(
            () ->
                service.requireGranted(authentication, ResourceType.UI, "seePage1"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("UI:seePage1");
  }

  @Test
  void indeterminateDecisionIsNeverCollapsedIntoDenial() {
    AuthorizationResult indeterminate = result(AuthorizationDecision.INDETERMINATE);
    when(identityResolver.resolve(authentication)).thenReturn(identity);
    when(engine.authorize(resource, identity)).thenReturn(indeterminate);
    AuthorizationService service = service();

    assertThat(service.evaluate(authentication, resource)).isSameAs(indeterminate);
    assertThatThrownBy(
            () -> service.isGranted(authentication, ResourceType.UI, "seePage1"))
        .isInstanceOf(IndeterminateAuthorizationException.class);
    assertThatThrownBy(
            () ->
                service.requireGranted(authentication, ResourceType.UI, "seePage1"))
        .isInstanceOf(IndeterminateAuthorizationException.class);
  }

  private AuthorizationService service() {
    return new AuthorizationService(engine, identityResolver, audit, observation);
  }

  private AuthorizationResult result(AuthorizationDecision decision) {
    return new AuthorizationResult(
        decision,
        decision == AuthorizationDecision.GRANTED
            ? AuthorizationReason.MATCHING_PERMISSION
            : decision == AuthorizationDecision.DENIED
                ? AuthorizationReason.MISSING_PERMISSION
                : AuthorizationReason.PROVIDER_UNAVAILABLE,
        "DEMO_UI_PAGE_1",
        decision == AuthorizationDecision.GRANTED ? "UI:DEMO_SEE_PAGE_1" : null,
        identity.cacheKey());
  }
}
