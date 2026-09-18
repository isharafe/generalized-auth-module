package io.github.isharafe.authorization.admin.ui;

import io.github.isharafe.authorization.admin.config.AuthorizationAdminAutoConfiguration;
import io.github.isharafe.authorization.config.AuthorizationProperties;
import io.github.isharafe.authorization.admin.config.AuthorizationAdminProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AuthorizationAdminAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "authorization",
    name = {"enabled", "admin.ui.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class AuthorizationAdminUiAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  AdminUiResourceConfiguration adminUiResourceConfiguration(
      AuthorizationAdminProperties properties) {
    return new AdminUiResourceConfiguration(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  AdminUiController adminUiController(
      AuthorizationAdminProperties properties, AuthorizationProperties authorization) {
    return new AdminUiController(properties, authorization);
  }
}
