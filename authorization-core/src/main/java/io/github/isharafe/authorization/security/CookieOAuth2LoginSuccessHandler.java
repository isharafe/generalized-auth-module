package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import io.github.isharafe.authorization.spi.IdentitySynchronizationProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

public final class CookieOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
  private static final Logger log =
      LoggerFactory.getLogger(CookieOAuth2LoginSuccessHandler.class);

  private final AuthorizationProperties.CookieOauth2 properties;
  private final OAuth2AuthorizedClientRepository clients;
  private final SpringAuthenticationIdentityResolver identityResolver;
  private final IdentitySynchronizationProvider synchronization;
  private final AuthorizationTokenCookies cookies;
  private final AuthorizationObservation observation;
  private final RedirectStrategy redirects = new DefaultRedirectStrategy();

  public CookieOAuth2LoginSuccessHandler(
      AuthorizationProperties properties,
      OAuth2AuthorizedClientRepository clients,
      SpringAuthenticationIdentityResolver identityResolver,
      IdentitySynchronizationProvider synchronization,
      AuthorizationTokenCookies cookies,
      AuthorizationObservation observation) {
    this.properties = properties.getSecurity().getCookieOauth2();
    this.clients = clients;
    this.identityResolver = identityResolver;
    this.synchronization = synchronization;
    this.cookies = cookies;
    this.observation = observation;
  }

  @Override
  public void onAuthenticationSuccess(
          @NonNull HttpServletRequest request,
          @NonNull HttpServletResponse response,
          @NonNull Authentication authentication)
      throws IOException {
    long started = System.nanoTime();
    boolean synchronizationEnabled = false;
    String result = "failure";
    String registrationId = properties.getRegistrationId();
    try {
      OAuth2AuthorizedClient client =
          clients.loadAuthorizedClient(registrationId, authentication, request);
      if (client == null) throw new IllegalStateException("OAuth2 authorized client is unavailable");
      AuthenticatedIdentity identity = identityResolver.resolve(authentication);
      synchronizationEnabled = synchronization.supported();
      if (synchronizationEnabled) synchronization.synchronize(identity);
      String idToken =
          authentication.getPrincipal() instanceof OidcUser oidc
              ? oidc.getIdToken().getTokenValue()
              : null;
      cookies.writeLoginTokens(
          response, client.getAccessToken(), client.getRefreshToken(), idToken);
      clients.removeAuthorizedClient(registrationId, authentication, request, response);
      if (request.getSession(false) != null) request.getSession(false).invalidate();
      redirects.sendRedirect(request, response, properties.getLoginSuccessUri());
      result = "success";
    } catch (RuntimeException exception) {
      fail(request, response, exception);
    } finally {
      observation.recordLoginInitialization(
          synchronizationEnabled,
          result,
          Duration.ofNanos(System.nanoTime() - started));
    }
  }

  private void fail(
      HttpServletRequest request,
      HttpServletResponse response,
      RuntimeException exception)
      throws IOException {
    SecurityContextHolder.clearContext();
    cookies.clearAll(response);
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    log.error("OAuth2 login could not establish local authorization state", exception);
    response.sendError(
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        "Authentication succeeded, but local authorization initialization failed");
  }
}
