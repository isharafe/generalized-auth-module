package io.github.isharafe.authorization.keycloak.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthorizationKeycloakPropertiesTest {
  @Test
  void validatesRequiredConnectionSettings() {
    AuthorizationKeycloakProperties properties = new AuthorizationKeycloakProperties();

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("authorization.keycloak.base-url");
  }

  @Test
  void normalizesBaseUrlResolvesIssuerAndValidatesLimits() {
    AuthorizationKeycloakProperties properties = validProperties();

    properties.validate();

    assertThat(properties.normalizedBaseUrl()).isEqualTo("https://id.example");
    assertThat(properties.resolvedIssuer()).isEqualTo("https://id.example/realms/company");

    properties.getHttp().setReadTimeout(Duration.ZERO);
    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read-timeout must be positive");
  }

  @Test
  void validatesEventCallbackSecuritySettingsOnlyWhenEnabled() {
    AuthorizationKeycloakProperties properties = validProperties();
    properties.getEvents().setEnabled(true);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("authorization.keycloak.events.secret");

    properties.getEvents().setSecret("short");
    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least 32 bytes");

    properties.getEvents().setSecret("0123456789abcdef0123456789abcdef");
    properties.validate();
    assertThat(properties.getEvents().getPath())
        .isEqualTo("/authorization/keycloak/events");
  }

  private AuthorizationKeycloakProperties validProperties() {
    AuthorizationKeycloakProperties properties = new AuthorizationKeycloakProperties();
    properties.setBaseUrl("https://id.example/");
    properties.setRealm("company");
    properties.setClientId("sync-client");
    properties.setClientSecret("secret");
    return properties;
  }
}
