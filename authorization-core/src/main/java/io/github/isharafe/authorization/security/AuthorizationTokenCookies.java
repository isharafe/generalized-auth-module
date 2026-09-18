package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

public final class AuthorizationTokenCookies {
  private final AuthorizationProperties.CookieOauth2 properties;

  public AuthorizationTokenCookies(AuthorizationProperties properties) {
    this.properties = properties.getSecurity().getCookieOauth2();
  }

  public void writeLoginTokens(
      HttpServletResponse response,
      OAuth2AccessToken accessToken,
      OAuth2RefreshToken refreshToken,
      String idToken) {
    add(
        response,
        properties.getAccessTokenCookie(),
        accessToken.getTokenValue(),
        maxAge(accessToken.getExpiresAt()));
    if (refreshToken != null) {
      add(
          response,
          properties.getRefreshTokenCookie(),
          refreshToken.getTokenValue(),
          maxAge(refreshToken.getExpiresAt()));
    }
    if (idToken != null
        && properties.getLogout().getMode() == AuthorizationProperties.LogoutMode.OIDC) {
      if (idToken.length() > 3500)
        throw new IllegalStateException("OIDC ID token is too large for the logout cookie");
      Instant idTokenCookieExpiry =
          refreshToken != null && refreshToken.getExpiresAt() != null
              ? refreshToken.getExpiresAt()
              : accessToken.getExpiresAt();
      add(response, properties.getIdTokenCookie(), idToken, maxAge(idTokenCookieExpiry));
    }
  }

  public void writeRefreshedTokens(
      HttpServletResponse response,
      OAuth2AccessToken accessToken,
      OAuth2RefreshToken refreshToken) {
    add(
        response,
        properties.getAccessTokenCookie(),
        accessToken.getTokenValue(),
        maxAge(accessToken.getExpiresAt()));
    if (refreshToken != null) {
      add(
          response,
          properties.getRefreshTokenCookie(),
          refreshToken.getTokenValue(),
          maxAge(refreshToken.getExpiresAt()));
    }
  }

  public void clearAll(HttpServletResponse response) {
    clear(response, properties.getAccessTokenCookie());
    clear(response, properties.getRefreshTokenCookie());
    clear(response, properties.getIdTokenCookie());
    ResponseCookie csrf =
        ResponseCookie.from(properties.getCsrf().getName(), "")
            .httpOnly(false)
            .secure(properties.getCsrf().isSecure())
            .sameSite(properties.getCsrf().getSameSite())
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, csrf.toString());
  }

  public String read(HttpServletRequest request, AuthorizationProperties.TokenCookie cookie) {
    return CookieOrHeaderBearerTokenResolver.cookie(request, cookie.getName());
  }

  private void add(
      HttpServletResponse response,
      AuthorizationProperties.TokenCookie cookie,
      String value,
      Duration maxAge) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(cookie.getName(), value)
            .httpOnly(true)
            .secure(cookie.isSecure())
            .sameSite(cookie.getSameSite())
            .path(cookie.getPath());
    if (maxAge != null) builder.maxAge(maxAge);
    response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
  }

  private void clear(
      HttpServletResponse response, AuthorizationProperties.TokenCookie cookie) {
    add(response, cookie, "", Duration.ZERO);
  }

  private Duration maxAge(Instant expiresAt) {
    if (expiresAt == null) return null;
    Duration value = Duration.between(Instant.now(), expiresAt);
    return value.isNegative() ? Duration.ZERO : value;
  }
}
