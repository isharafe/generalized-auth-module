package com.example.authorization.admin.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AuthorizationAdminPropertiesTest {
  @Test
  void providesAdminDefaults() {
    AuthorizationAdminProperties properties = new AuthorizationAdminProperties();

    assertThat(properties.getApi().isEnabled()).isTrue();
    assertThat(properties.getApi().getBasePath()).isEqualTo("/authorization-admin/api");
    assertThat(properties.getUi().isEnabled()).isTrue();
    assertThat(properties.getUi().getBasePath()).isEqualTo("/authorization-admin");
  }

  @Test
  void bindsAdminConfigurationIndependentlyFromCoreProperties() {
    AuthorizationAdminProperties properties =
        new Binder(
                new MapConfigurationPropertySource(
                    Map.of(
                        "authorization.admin.api.enabled", "false",
                        "authorization.admin.api.base-path", "/management/api",
                        "authorization.admin.ui.enabled", "true",
                        "authorization.admin.ui.base-path", "/management")))
            .bind("authorization.admin", Bindable.of(AuthorizationAdminProperties.class))
            .get();

    assertThat(properties.getApi().isEnabled()).isFalse();
    assertThat(properties.getApi().getBasePath()).isEqualTo("/management/api");
    assertThat(properties.getUi().isEnabled()).isTrue();
    assertThat(properties.getUi().getBasePath()).isEqualTo("/management");
  }
}
