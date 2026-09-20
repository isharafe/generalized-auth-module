package io.github.isharafe.authorization.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.isharafe.authorization.security.UrlSecurityPolicyDecision;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class AuthorizationSecurityAutoConfigurationTest {
  @Test
  void publishesInspectablePoliciesForTheDefaultFilterChains() {
    AuthorizationProperties properties = properties();
    properties.getPermissionsApi().setEndpoint("/custom/user/permissions");

    assertThat(AuthorizationSecurityAutoConfiguration.defaultUrlSecurityPolicies(properties))
        .anySatisfy(
            policy -> {
              assertThat(policy.code()).isEqualTo("AUTHORIZATION_USER_PERMISSIONS");
              assertThat(policy.pattern()).isEqualTo("*:/custom/user/permissions");
              assertThat(policy.decision()).isEqualTo(UrlSecurityPolicyDecision.AUTHENTICATED);
            })
        .anySatisfy(
            policy -> {
              assertThat(policy.code()).isEqualTo("AUTHORIZATION_RESOURCE_RULES");
              assertThat(policy.pattern()).isEqualTo("*:/**");
              assertThat(policy.decision()).isEqualTo(UrlSecurityPolicyDecision.RESOURCE_RULES);
            });
  }

  @Test
  void acceptsAValidLocalLogoutConfiguration() {
    AuthorizationProperties properties = properties();

    assertThatCode(
            () ->
                AuthorizationSecurityAutoConfiguration.validate(properties, registrations(false)))
        .doesNotThrowAnyException();
  }

  @Test
  void requiresTheConfiguredRegistration() {
    AuthorizationProperties properties = properties();
    properties.getSecurity().getCookieOauth2().setRegistrationId("missing");

    assertThatThrownBy(
            () ->
                AuthorizationSecurityAutoConfiguration.validate(properties, registrations(false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No OAuth2 client registration exists");
  }

  @Test
  void rejectsUnsafeOrAmbiguousCookieConfiguration() {
    AuthorizationProperties duplicateNames = properties();
    duplicateNames
        .getSecurity()
        .getCookieOauth2()
        .getRefreshTokenCookie()
        .setName("AUTHORIZATION_ACCESS_TOKEN");

    assertThatThrownBy(
            () ->
                AuthorizationSecurityAutoConfiguration.validate(
                    duplicateNames, registrations(false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cookie names must be unique");

    AuthorizationProperties insecure = properties();
    insecure.getSecurity().getCookieOauth2().getCsrf().setSameSite("None");
    insecure.getSecurity().getCookieOauth2().getCsrf().setSecure(false);

    assertThatThrownBy(
            () ->
                AuthorizationSecurityAutoConfiguration.validate(insecure, registrations(false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("same-site=None requires secure=true");
  }

  @Test
  void rejectsInvalidEndpointAndCookiePathRelationships() {
    AuthorizationProperties remoteEndpoint = properties();
    remoteEndpoint
        .getSecurity()
        .getCookieOauth2()
        .setRefreshEndpoint("https://example.test/refresh");

    assertThatThrownBy(
            () ->
                AuthorizationSecurityAutoConfiguration.validate(
                    remoteEndpoint, registrations(false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must start with a single /");

    AuthorizationProperties wrongCookiePath = properties();
    wrongCookiePath
        .getSecurity()
        .getCookieOauth2()
        .getRefreshTokenCookie()
        .setPath("/unrelated");

    assertThatThrownBy(
            () ->
                AuthorizationSecurityAutoConfiguration.validate(
                    wrongCookiePath, registrations(false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must contain the refresh endpoint");
  }

  @Test
  void oidcLogoutRequiresProviderMetadata() {
    AuthorizationProperties properties = properties();
    properties
        .getSecurity()
        .getCookieOauth2()
        .getLogout()
        .setMode(AuthorizationProperties.LogoutMode.OIDC);

    assertThatThrownBy(
            () ->
                AuthorizationSecurityAutoConfiguration.validate(properties, registrations(false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("end_session_endpoint");

    assertThatCode(
            () -> AuthorizationSecurityAutoConfiguration.validate(properties, registrations(true)))
        .doesNotThrowAnyException();
  }

  private AuthorizationProperties properties() {
    AuthorizationProperties properties = new AuthorizationProperties();
    properties.getSecurity().getCookieOauth2().setRegistrationId("keycloak");
    return properties;
  }

  private ClientRegistrationRepository registrations(boolean oidcLogout) {
    ClientRegistration.Builder registration =
        ClientRegistration.withRegistrationId("keycloak")
            .clientId("browser-client")
            .clientSecret("demo-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://id.example/authorize")
            .tokenUri("https://id.example/token")
            .userInfoUri("https://id.example/userinfo")
            .userNameAttributeName("sub")
            .clientName("Keycloak");
    if (oidcLogout) {
      registration.providerConfigurationMetadata(
          Map.of("end_session_endpoint", "https://id.example/logout"));
    }
    return new InMemoryClientRegistrationRepository(registration.build());
  }
}
