package com.example.authorization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.AuthorizationDecision;
import com.example.authorization.domain.AuthorizationReason;
import com.example.authorization.domain.AuthorizationResult;
import com.example.authorization.engine.AuthorizationEngine;
import com.example.authorization.spi.AuthorizationAuditPublisher;
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
    DynamicRequestAuthorizationManager manager =
        new DynamicRequestAuthorizationManager(engine, authentication -> identity, audit);
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
