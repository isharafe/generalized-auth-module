package io.github.isharafe.authorization.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

class AuthorizationTokenCookiesTest {
  @Test
  void writesHttpOnlyTokensWithNarrowPathsAndOnlyPersistsIdTokenForOidcLogout() {
    AuthorizationProperties properties = new AuthorizationProperties();
    AuthorizationTokenCookies cookies = new AuthorizationTokenCookies(properties);
    MockHttpServletResponse response = new MockHttpServletResponse();
    Instant now = Instant.now();

    cookies.writeLoginTokens(
        response,
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access",
            now,
            now.plusSeconds(300)),
        new OAuth2RefreshToken("refresh", now, now.plusSeconds(600)),
        "id-token");

    List<String> localHeaders = response.getHeaders(HttpHeaders.SET_COOKIE);
    assertThat(localHeaders).hasSize(2);
    assertThat(localHeaders)
        .anySatisfy(
            header ->
                assertThat(header)
                    .contains("AUTHORIZATION_ACCESS_TOKEN=access", "Path=/", "HttpOnly", "Secure"))
        .anySatisfy(
            header ->
                assertThat(header)
                    .contains(
                        "AUTHORIZATION_REFRESH_TOKEN=refresh",
                        "Path=/authorization/security",
                        "HttpOnly",
                        "Secure"));

    properties
        .getSecurity()
        .getCookieOauth2()
        .getLogout()
        .setMode(AuthorizationProperties.LogoutMode.OIDC);
    MockHttpServletResponse oidcResponse = new MockHttpServletResponse();
    cookies.writeLoginTokens(
        oidcResponse,
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access",
            now,
            now.plusSeconds(300)),
        null,
        "id-token");

    assertThat(oidcResponse.getHeaders(HttpHeaders.SET_COOKIE))
        .anySatisfy(
            header ->
                assertThat(header)
                    .contains(
                        "AUTHORIZATION_ID_TOKEN=id-token",
                        "Path=/authorization/security/logout",
                        "HttpOnly"));
  }

  @Test
  void clearsEveryAuthorizationAndCsrfCookie() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    new AuthorizationTokenCookies(new AuthorizationProperties()).clearAll(response);

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .hasSize(4)
        .allSatisfy(header -> assertThat(header).contains("Max-Age=0"));
  }
}
