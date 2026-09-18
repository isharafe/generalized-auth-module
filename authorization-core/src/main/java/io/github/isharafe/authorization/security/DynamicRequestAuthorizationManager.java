package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

public final class DynamicRequestAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {
  private final AuthorizationService authorization;

  public DynamicRequestAuthorizationManager(AuthorizationService authorization) {
    this.authorization = authorization;
  }

  @Override
  public AuthorizationDecision authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    HttpServletRequest request = context.getRequest();
    String path = applicationPath(request);
    return new AuthorizationDecision(
        authorization.isGranted(
            authentication.get(),
            ResourceType.URL,
            "%s:%s".formatted(request.getMethod(), path)));
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
