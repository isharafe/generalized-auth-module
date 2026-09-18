package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

public final class OidcCookieLogoutSuccessHandler implements LogoutSuccessHandler {
  private static final Logger log =
      LoggerFactory.getLogger(OidcCookieLogoutSuccessHandler.class);

  private final AuthorizationProperties.CookieOauth2 properties;
  private final AuthorizationTokenCookies cookies;
  private final JwtDecoder decoder;
  private final OidcClientInitiatedLogoutSuccessHandler oidc;
  private final LocalCookieLogoutSuccessHandler local;

  public OidcCookieLogoutSuccessHandler(
      AuthorizationProperties properties,
      AuthorizationTokenCookies cookies,
      JwtDecoder decoder,
      ClientRegistrationRepository registrations) {
    this.properties = properties.getSecurity().getCookieOauth2();
    this.cookies = cookies;
    this.decoder = decoder;
    this.oidc = new OidcClientInitiatedLogoutSuccessHandler(registrations);
    this.oidc.setPostLogoutRedirectUri(
        properties
            .getSecurity()
            .getCookieOauth2()
            .getLogout()
            .getPostLogoutRedirectUri());
    this.local = new LocalCookieLogoutSuccessHandler(properties);
  }

  @Override
  public void onLogoutSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication)
      throws IOException, ServletException {
    String raw = cookies.read(request, properties.getIdTokenCookie());
    if (raw == null) {
      local.onLogoutSuccess(request, response, authentication);
      return;
    }
    try {
      Jwt jwt = decoder.decode(raw);
      OidcIdToken idToken =
          new OidcIdToken(raw, jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getClaims());
      DefaultOidcUser user = new DefaultOidcUser(List.of(), idToken);
      OAuth2AuthenticationToken oidcAuthentication =
          new OAuth2AuthenticationToken(
              user, user.getAuthorities(), properties.getRegistrationId());
      oidc.onLogoutSuccess(request, response, oidcAuthentication);
    } catch (RuntimeException failure) {
      log.warn("OIDC logout hint could not be validated; completing local logout");
      local.onLogoutSuccess(request, response, authentication);
    }
  }
}
