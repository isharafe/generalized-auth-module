package io.github.isharafe.authorization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.AuthorizationDecision;
import io.github.isharafe.authorization.domain.AuthorizationReason;
import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.engine.AuthorizationEngine;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class DynamicRequestAuthorizationManagerPathTest {
  @Test
  void removesOnlyTheServletContextPathFromTheProtectedResource() {
    AuthorizationEngine engine = mock(AuthorizationEngine.class);
    AuthorizationAuditPublisher audit = mock(AuthorizationAuditPublisher.class);
    AuthenticatedIdentity identity = new AuthenticatedIdentity("local", "alice", "alice");
    AuthorizationResult granted =
        AuthorizationResult.of(
            AuthorizationDecision.GRANTED,
            AuthorizationReason.PUBLIC_RESOURCE,
            null,
            null,
            identity);
    when(engine.authorize(argThat(resource -> resource.pattern().equals("GET:/demo/public")), eq(identity)))
        .thenReturn(granted);
    AuthorizationService service =
        new AuthorizationService(
            engine,
            authentication -> identity,
            audit,
            mock(AuthorizationObservation.class));
    DynamicRequestAuthorizationManager manager =
        new DynamicRequestAuthorizationManager(service);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/demo/public");
    request.setContextPath("/app");

    var decision =
        manager.authorize(
            () -> new TestingAuthenticationToken("alice", null),
            new RequestAuthorizationContext(request));

    assertThat(decision.isGranted()).isTrue();
    verify(engine)
        .authorize(argThat(resource -> resource.pattern().equals("GET:/demo/public")), eq(identity));
  }
}
