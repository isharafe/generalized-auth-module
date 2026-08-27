package com.example.authorization.demo;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.security.SpringAuthenticationIdentityResolver;
import com.example.authorization.spi.IdentitySynchronizationProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeycloakDemoLoginSuccessHandler implements AuthenticationSuccessHandler {
  private static final Logger log =
      LoggerFactory.getLogger(KeycloakDemoLoginSuccessHandler.class);
  private final IdentitySynchronizationProvider synchronization;
  private final SpringAuthenticationIdentityResolver identityResolver;
  private final RedirectStrategy redirects = new DefaultRedirectStrategy();

  public KeycloakDemoLoginSuccessHandler(
      IdentitySynchronizationProvider synchronization,
      SpringAuthenticationIdentityResolver identityResolver) {
    this.synchronization = synchronization;
    this.identityResolver = identityResolver;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication)
      throws IOException, ServletException {
    AuthenticatedIdentity identity = identityResolver.resolve(authentication);
    try {
      synchronization.synchronize(identity);
      redirects.sendRedirect(request, response, "/demo-ui/");
    } catch (RuntimeException exception) {
      SecurityContextHolder.clearContext();
      log.error(
          "Keycloak login synchronization failed for issuer={} subject={}",
          identity.issuer(),
          identity.subject(),
          exception);
      response.sendError(
          HttpStatus.SERVICE_UNAVAILABLE.value(),
          "Authentication succeeded, but local authorization synchronization failed");
    }
  }
}
