package com.example.authorization.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.authorization.persistence.repository.UserRepository;
import com.example.authorization.cache.CachingEntitlementProvider;
import com.example.authorization.spi.EntitlementProvider;
import com.example.authorization.spi.IdentitySynchronizationProvider;
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
  void providesAdminUiDefaults() {
    AuthorizationProperties properties = new AuthorizationProperties();

    assertThat(properties.getAdmin().getUi().isEnabled()).isTrue();
    assertThat(properties.getAdmin().getUi().getBasePath()).isEqualTo("/authorization-admin");
  }

  @Test
  void disablesTheEntitlementCacheWhenConfigured() {
    AuthorizationProperties properties = new AuthorizationProperties();
    properties.getCache().getEntitlements().setEnabled(false);

    EntitlementProvider provider =
        new AuthorizationAutoConfiguration()
            .entitlementProvider(mock(UserRepository.class), properties);

    assertThat(provider).isNotInstanceOf(CachingEntitlementProvider.class);
  }

  @Test
  void rejectsANonFailClosedDefaultDecision() {
    AuthorizationProperties properties = new AuthorizationProperties();
    properties.setDefaultDecision("PERMIT");
    IdentitySynchronizationProvider synchronization = new IdentitySynchronizationProvider() {};
    var guard =
        new AuthorizationAutoConfiguration().authorizationSourceGuard(properties, synchronization);

    assertThatThrownBy(() -> guard.run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be DENY");
  }
}
