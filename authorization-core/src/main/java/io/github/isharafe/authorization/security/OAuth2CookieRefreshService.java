package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import java.time.Instant;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

public final class OAuth2CookieRefreshService {
  private final AuthorizationProperties.CookieOauth2 properties;
  private final ClientRegistrationRepository registrations;
  private final RestClientRefreshTokenTokenResponseClient tokenClient;
  private final AuthorizationTokenCookies cookies;

  public OAuth2CookieRefreshService(
      AuthorizationProperties properties,
      ClientRegistrationRepository registrations,
      RestClientRefreshTokenTokenResponseClient tokenClient,
      AuthorizationTokenCookies cookies) {
    this.properties = properties.getSecurity().getCookieOauth2();
    this.registrations = registrations;
    this.tokenClient = tokenClient;
    this.cookies = cookies;
  }

  public boolean refresh(
      jakarta.servlet.http.HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response) {
    String refreshValue = cookies.read(request, properties.getRefreshTokenCookie());
    if (refreshValue == null) return false;
    String accessValue = cookies.read(request, properties.getAccessTokenCookie());
    if (accessValue == null) accessValue = "expired-access-token";

    ClientRegistration registration =
        registrations.findByRegistrationId(properties.getRegistrationId());
    if (registration == null) {
      cookies.clearAll(response);
      return false;
    }

    Instant now = Instant.now();
    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            accessValue,
            now.minusSeconds(60),
            now.minusSeconds(1));
    OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(refreshValue, now.minusSeconds(60));
    try {
      OAuth2AccessTokenResponse refreshed =
          tokenClient.getTokenResponse(
              new OAuth2RefreshTokenGrantRequest(registration, accessToken, refreshToken));
      cookies.writeRefreshedTokens(
          response,
          refreshed.getAccessToken(),
          refreshed.getRefreshToken());
      return true;
    } catch (RuntimeException failure) {
      cookies.clearAll(response);
      return false;
    }
  }
}
