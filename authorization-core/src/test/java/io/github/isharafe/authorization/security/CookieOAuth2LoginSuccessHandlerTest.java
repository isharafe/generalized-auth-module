package io.github.isharafe.authorization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.spi.IdentitySynchronizationProvider;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

class CookieOAuth2LoginSuccessHandlerTest {
  private final AuthorizationProperties properties = new AuthorizationProperties();
  private final OAuth2AuthorizedClientRepository clients =
      mock(OAuth2AuthorizedClientRepository.class);
  private final SpringAuthenticationIdentityResolver resolver =
      mock(SpringAuthenticationIdentityResolver.class);
  private final IdentitySynchronizationProvider synchronization =
      mock(IdentitySynchronizationProvider.class);
  private final Authentication authentication = mock(Authentication.class);
  private final AuthorizationTokenCookies cookies = new AuthorizationTokenCookies(properties);
  private final AuthenticatedIdentity identity =
      new AuthenticatedIdentity("https://id.example/realms/demo", "user-1", "alice");

  @BeforeEach
  void configure() {
    properties.getSecurity().getCookieOauth2().setRegistrationId("keycloak");
    properties.getSecurity().getCookieOauth2().setLoginSuccessUri("/demo-ui/");
  }

  @Test
  void synchronizesWritesTokensRemovesTheAuthorizedClientAndInvalidatesTheSession()
      throws Exception {
    Instant now = Instant.now();
    OAuth2AuthorizedClient client =
        new OAuth2AuthorizedClient(
            registration(),
            "alice",
            new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                now,
                now.plusSeconds(300)),
            new OAuth2RefreshToken("refresh-token", now, now.plusSeconds(900)));
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpSession session = (MockHttpSession) request.getSession();
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(clients.loadAuthorizedClient("keycloak", authentication, request)).thenReturn(client);
    when(resolver.resolve(authentication)).thenReturn(identity);
    when(synchronization.supported()).thenReturn(true);

    handler().onAuthenticationSuccess(request, response, authentication);

    verify(synchronization).synchronize(identity);
    verify(clients).removeAuthorizedClient("keycloak", authentication, request, response);
    assertThat(session.isInvalid()).isTrue();
    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getRedirectedUrl()).endsWith("/demo-ui/");
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .anySatisfy(value -> assertThat(value).contains("AUTHORIZATION_ACCESS_TOKEN=access-token"))
        .anySatisfy(
            value -> assertThat(value).contains("AUTHORIZATION_REFRESH_TOKEN=refresh-token"));
  }

  @Test
  void skipsSynchronizationWhenTheSelectedSourceDoesNotSupportIt() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(clients.loadAuthorizedClient("keycloak", authentication, request))
        .thenReturn(clientWithoutRefreshToken());
    when(resolver.resolve(authentication)).thenReturn(identity);
    when(synchronization.supported()).thenReturn(false);

    handler().onAuthenticationSuccess(request, response, authentication);

    verify(synchronization).supported();
    assertThat(response.getStatus()).isEqualTo(302);
  }

  @Test
  void failsClosedAndClearsCookiesWhenSynchronizationFails() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpSession session = (MockHttpSession) request.getSession();
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(clients.loadAuthorizedClient("keycloak", authentication, request))
        .thenReturn(clientWithoutRefreshToken());
    when(resolver.resolve(authentication)).thenReturn(identity);
    when(synchronization.supported()).thenReturn(true);
    doThrow(new IllegalStateException("provider unavailable"))
        .when(synchronization)
        .synchronize(identity);

    handler().onAuthenticationSuccess(request, response, authentication);

    assertThat(session.isInvalid()).isTrue();
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getErrorMessage()).contains("local authorization initialization failed");
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .hasSize(4)
        .allSatisfy(value -> assertThat(value).contains("Max-Age=0"));
  }

  @Test
  void failsClosedWhenTheAuthorizedClientIsUnavailable() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler().onAuthenticationSuccess(request, response, authentication);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(4);
  }

  private CookieOAuth2LoginSuccessHandler handler() {
    return new CookieOAuth2LoginSuccessHandler(
        properties, clients, resolver, synchronization, cookies);
  }

  private OAuth2AuthorizedClient clientWithoutRefreshToken() {
    Instant now = Instant.now();
    return new OAuth2AuthorizedClient(
        registration(),
        "alice",
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access-token",
            now,
            now.plusSeconds(300)));
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
