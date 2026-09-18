package io.github.isharafe.authorization.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.cache.CachingEntitlementProvider;
import io.github.isharafe.authorization.observability.NoOpAuthorizationObservation;
import io.github.isharafe.authorization.spi.EntitlementProvider;
import io.github.isharafe.authorization.spi.IdentitySynchronizationProvider;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AuthorizationPropertiesTest {
  @Test
  void bindsTheDocumentedCacheStructure() {
    AuthorizationProperties properties =
        new Binder(
                new MapConfigurationPropertySource(
                    Map.of(
                        "authorization.default-decision", "DENY",
                        "authorization.cache.resource-rules.enabled", "false",
                        "authorization.cache.resource-rules.ttl", "12m",
                        "authorization.cache.entitlements.enabled", "true",
                        "authorization.cache.entitlements.ttl", "45s")))
            .bind("authorization", Bindable.of(AuthorizationProperties.class))
            .get();

    assertThat(properties.getDefaultDecision()).isEqualTo("DENY");
    assertThat(properties.getCache().getResourceRules().isEnabled()).isFalse();
    assertThat(properties.getCache().getResourceRules().getTtl()).isEqualTo(Duration.ofMinutes(12));
    assertThat(properties.getCache().getEntitlements().isEnabled()).isTrue();
    assertThat(properties.getCache().getEntitlements().getTtl()).isEqualTo(Duration.ofSeconds(45));
  }

  @Test
  void bindsCookieOauth2AndLogoutConfiguration() {
    AuthorizationProperties properties =
        new Binder(
                new MapConfigurationPropertySource(
                    Map.of(
                        "authorization.security.cookie-oauth2.enabled", "true",
                        "authorization.security.cookie-oauth2.registration-id", "keycloak",
                        "authorization.security.cookie-oauth2.logout.mode", "OIDC",
                        "authorization.security.cookie-oauth2.logout.revoke-refresh-token", "false",
                        "authorization.security.cookie-oauth2.access-token-cookie.secure", "false")))
            .bind("authorization", Bindable.of(AuthorizationProperties.class))
            .get();

    var cookieOauth2 = properties.getSecurity().getCookieOauth2();
    assertThat(cookieOauth2.isEnabled()).isTrue();
    assertThat(cookieOauth2.getRegistrationId()).isEqualTo("keycloak");
    assertThat(cookieOauth2.getLogout().getMode())
        .isEqualTo(AuthorizationProperties.LogoutMode.OIDC);
    assertThat(cookieOauth2.getLogout().isRevokeRefreshToken()).isFalse();
    assertThat(cookieOauth2.getAccessTokenCookie().isSecure()).isFalse();
    assertThat(cookieOauth2.getRefreshTokenCookie().getPath())
        .isEqualTo("/authorization/security");
  }

  @Test
  void disablesTheEntitlementCacheWhenConfigured() {
    AuthorizationProperties properties = new AuthorizationProperties();
    properties.getCache().getEntitlements().setEnabled(false);

    EntitlementProvider provider =
        new AuthorizationAutoConfiguration()
            .entitlementProvider(
                mock(UserRepository.class), properties, new NoOpAuthorizationObservation());

    assertThat(provider).isNotInstanceOf(CachingEntitlementProvider.class);
  }

  @Test
  void rejectsANonFailClosedDefaultDecision() {
    AuthorizationProperties properties = new AuthorizationProperties();
    properties.setDefaultDecision("PERMIT");
    @SuppressWarnings("unchecked")
    org.springframework.beans.factory.ObjectProvider<IdentitySynchronizationProvider>
        synchronizationProviders =
            mock(org.springframework.beans.factory.ObjectProvider.class);
    org.mockito.Mockito.when(synchronizationProviders.getIfAvailable())
        .thenReturn(new IdentitySynchronizationProvider() {});
    var guard =
        new AuthorizationAutoConfiguration()
            .authorizationSourceGuard(properties, synchronizationProviders);

    assertThatThrownBy(() -> guard.run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be DENY");
  }
}
