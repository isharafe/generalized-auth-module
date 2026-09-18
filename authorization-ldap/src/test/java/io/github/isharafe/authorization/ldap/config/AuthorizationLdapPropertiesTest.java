package io.github.isharafe.authorization.ldap.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorizationLdapPropertiesTest {
  @Test
  void validatesRequiredConnectionSettings() {
    AuthorizationLdapProperties properties = new AuthorizationLdapProperties();

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("authorization.ldap.urls");
  }

  @Test
  void normalizesUrlsResolvesIssuerAndValidatesSearchConfiguration() {
    AuthorizationLdapProperties properties = validProperties();

    properties.validate();

    assertThat(properties.normalizedUrls()).containsExactly("ldaps://directory.example:636");
    assertThat(properties.resolvedIssuer())
        .isEqualTo("ldaps://directory.example:636/dc=example,dc=com");

    properties.getGroup().setSearchFilter("(member=missing-placeholder)");
    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("placeholder {0}");
  }

  @Test
  void rejectsPartialBindCredentialsAndInvalidAttributeNames() {
    AuthorizationLdapProperties properties = validProperties();
    properties.setBindDn("cn=sync,dc=example,dc=com");

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("configured together");

    properties.setBindPassword("secret");
    properties.getUser().setAuthorityAttributes(List.of("department)(uid=*"));
    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid LDAP attribute name");
  }

  @Test
  void rejectsInvalidTimeoutsAndPageSizes() {
    AuthorizationLdapProperties properties = validProperties();
    properties.setReadTimeout(Duration.ZERO);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read-timeout must be positive");

    properties.setReadTimeout(Duration.ofSeconds(1));
    properties.getSync().setPageSize(0);
    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("page-size must be between 1 and 1000");
  }

  @Test
  void supportsBinaryStableIdentityConfigurationForActiveDirectory() {
    AuthorizationLdapProperties properties = validProperties();
    properties.getUser().setIdentityAttribute("objectGUID");
    properties.getUser().setIdentityAttributeBinary(true);

    properties.validate();

    assertThat(properties.getUser().isIdentityAttributeBinary()).isTrue();
  }

  private AuthorizationLdapProperties validProperties() {
    AuthorizationLdapProperties properties = new AuthorizationLdapProperties();
    properties.setUrls(List.of("ldaps://directory.example:636/"));
    properties.setBaseDn("dc=example,dc=com");
    return properties;
  }
}
