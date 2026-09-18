package io.github.isharafe.authorization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

class OAuth2CookieRefreshServiceTest {
  private final AuthorizationProperties properties = new AuthorizationProperties();
  private final ClientRegistrationRepository registrations =
      mock(ClientRegistrationRepository.class);
  private final RestClientRefreshTokenTokenResponseClient tokenClient =
      mock(RestClientRefreshTokenTokenResponseClient.class);
  private final AuthorizationTokenCookies cookies = new AuthorizationTokenCookies(properties);

  @BeforeEach
  void configure() {
    properties.getSecurity().getCookieOauth2().setRegistrationId("keycloak");
    when(registrations.findByRegistrationId("keycloak")).thenReturn(registration());
  }

  @Test
  void exchangesTheRefreshTokenAndRotatesBothCookies() {
    MockHttpServletRequest request = requestWithTokens("old-access", "old-refresh");
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(tokenClient.getTokenResponse(any()))
        .thenReturn(
            OAuth2AccessTokenResponse.withToken("new-access")
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(300)
                .refreshToken("new-refresh")
                .build());

    assertThat(service().refresh(request, response)).isTrue();

    ArgumentCaptor<OAuth2RefreshTokenGrantRequest> exchange =
        ArgumentCaptor.forClass(OAuth2RefreshTokenGrantRequest.class);
    verify(tokenClient).getTokenResponse(exchange.capture());
    assertThat(exchange.getValue().getAccessToken().getTokenValue()).isEqualTo("old-access");
    assertThat(exchange.getValue().getRefreshToken().getTokenValue()).isEqualTo("old-refresh");
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .hasSize(2)
        .anySatisfy(value -> assertThat(value).contains("AUTHORIZATION_ACCESS_TOKEN=new-access"))
        .anySatisfy(value -> assertThat(value).contains("AUTHORIZATION_REFRESH_TOKEN=new-refresh"));
  }

  @Test
  void leavesTheExistingRefreshCookieUntouchedWhenTheProviderDoesNotRotateIt() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(tokenClient.getTokenResponse(any()))
        .thenReturn(
            OAuth2AccessTokenResponse.withToken("new-access")
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(300)
                .build());

    assertThat(service().refresh(requestWithTokens(null, "old-refresh"), response)).isTrue();

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .singleElement()
        .satisfies(value -> assertThat(value).contains("AUTHORIZATION_ACCESS_TOKEN=new-access"));
  }

  @Test
  void clearsCookiesAndReturnsFalseWhenTheProviderRejectsRefresh() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(tokenClient.getTokenResponse(any())).thenThrow(new IllegalStateException("invalid grant"));

    assertThat(service().refresh(requestWithTokens("old-access", "old-refresh"), response))
        .isFalse();

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .hasSize(4)
        .allSatisfy(value -> assertThat(value).contains("Max-Age=0"));
  }

  @Test
  void rejectsRequestsWithoutARefreshCookieWithoutCallingTheProvider() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThat(service().refresh(new MockHttpServletRequest(), response)).isFalse();

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  void clearsStaleCookiesWhenTheConfiguredRegistrationDisappears() {
    when(registrations.findByRegistrationId("keycloak")).thenReturn(null);
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThat(service().refresh(requestWithTokens("old-access", "old-refresh"), response))
        .isFalse();

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(4);
  }

  private OAuth2CookieRefreshService service() {
    return new OAuth2CookieRefreshService(properties, registrations, tokenClient, cookies);
  }

  private MockHttpServletRequest requestWithTokens(String accessToken, String refreshToken) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (accessToken == null) {
      request.setCookies(new Cookie("AUTHORIZATION_REFRESH_TOKEN", refreshToken));
    } else {
      request.setCookies(
          new Cookie("AUTHORIZATION_ACCESS_TOKEN", accessToken),
          new Cookie("AUTHORIZATION_REFRESH_TOKEN", refreshToken));
    }
    return request;
  }

  private ClientRegistration registration() {
    return ClientRegistration.withRegistrationId("keycloak")
        .clientId("browser-client")
        .clientSecret("demo-secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("https://id.example/authorize")
        .tokenUri("https://id.example/token")
        .userInfoUri("https://id.example/userinfo")
        .userNameAttributeName("sub")
        .clientName("Keycloak")
        .build();
  }
}
