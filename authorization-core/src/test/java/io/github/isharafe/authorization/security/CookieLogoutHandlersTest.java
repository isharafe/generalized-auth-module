package io.github.isharafe.authorization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CookieLogoutHandlersTest {
  @Test
  void localLogoutResolvesTheBaseUrlAndContextPath() throws Exception {
    AuthorizationProperties properties = properties();
    properties
        .getSecurity()
        .getCookieOauth2()
        .getLogout()
        .setPostLogoutRedirectUri("{baseUrl}/signed-out");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setScheme("https");
    request.setServerName("app.example");
    request.setServerPort(443);
    request.setContextPath("/employees");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new LocalCookieLogoutSuccessHandler(properties)
        .onLogoutSuccess(request, response, null);

    assertThat(response.getRedirectedUrl())
        .isEqualTo("https://app.example/employees/signed-out");
  }

  @Test
  void oidcLogoutFallsBackToLocalWhenTheHintCannotBeValidated() throws Exception {
    AuthorizationProperties properties = properties();
    properties
        .getSecurity()
        .getCookieOauth2()
        .getLogout()
        .setMode(AuthorizationProperties.LogoutMode.OIDC);
    properties
        .getSecurity()
        .getCookieOauth2()
        .getLogout()
        .setPostLogoutRedirectUri("/signed-out");
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode("bad-id-token")).thenThrow(new IllegalArgumentException("invalid JWT"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("AUTHORIZATION_ID_TOKEN", "bad-id-token"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    new OidcCookieLogoutSuccessHandler(
            properties,
            new AuthorizationTokenCookies(properties),
            decoder,
            registrations())
        .onLogoutSuccess(request, response, null);

    assertThat(response.getRedirectedUrl()).isEqualTo("/signed-out");
  }

  @Test
  void oidcLogoutUsesAValidatedIdTokenAsTheProviderHint() throws Exception {
    AuthorizationProperties properties = properties();
    properties
        .getSecurity()
        .getCookieOauth2()
        .getLogout()
        .setMode(AuthorizationProperties.LogoutMode.OIDC);
    properties
        .getSecurity()
        .getCookieOauth2()
        .getLogout()
        .setPostLogoutRedirectUri("{baseUrl}/signed-out");
    JwtDecoder decoder =
        token ->
            Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .issuer("https://id.example")
                .subject("alice")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setScheme("https");
    request.setServerName("app.example");
    request.setServerPort(443);
    request.setCookies(new Cookie("AUTHORIZATION_ID_TOKEN", "id-token"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    new OidcCookieLogoutSuccessHandler(
            properties,
            new AuthorizationTokenCookies(properties),
            decoder,
            registrations())
        .onLogoutSuccess(request, response, null);

    assertThat(response.getRedirectedUrl())
        .startsWith("https://id.example/logout?")
        .contains("id_token_hint=id-token")
        .contains("post_logout_redirect_uri=");
  }

  @Test
  void revokesTheRefreshTokenWithClientSecretBasic() {
    AuthorizationProperties properties = properties();
    RestClient.Builder restClient = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClient).build();
    String expectedBasic =
        "Basic "
            + Base64.getEncoder()
                .encodeToString("browser-client:demo-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    server
        .expect(once(), requestTo("https://id.example/revoke"))
        .andExpect(method(POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, expectedBasic))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("token=refresh-token")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.containsString("token_type_hint=refresh_token")))
        .andRespond(withSuccess());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("AUTHORIZATION_REFRESH_TOKEN", "refresh-token"));

    new OAuth2RefreshTokenRevokingLogoutHandler(
            properties,
            registrations(),
            new AuthorizationTokenCookies(properties),
            restClient.build())
        .logout(request, new MockHttpServletResponse(), null);

    server.verify();
  }

  private AuthorizationProperties properties() {
    AuthorizationProperties properties = new AuthorizationProperties();
    properties.getSecurity().getCookieOauth2().setRegistrationId("keycloak");
    return properties;
  }

  private ClientRegistrationRepository registrations() {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("keycloak")
            .clientId("browser-client")
            .clientSecret("demo-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://id.example/authorize")
            .tokenUri("https://id.example/token")
            .userInfoUri("https://id.example/userinfo")
            .userNameAttributeName("sub")
            .clientName("Keycloak")
            .providerConfigurationMetadata(
                Map.of(
                    "end_session_endpoint", "https://id.example/logout",
                    "revocation_endpoint", "https://id.example/revoke"))
            .build();
    return new InMemoryClientRegistrationRepository(registration);
  }
}
