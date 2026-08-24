package com.example.authorization.ui;

import com.example.authorization.config.AuthorizationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(
    prefix = "authorization",
    name = {"enabled", "admin.ui.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class AuthorizationAdminUiAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  AdminUiResourceConfiguration adminUiResourceConfiguration(
      AuthorizationProperties properties) {
    return new AdminUiResourceConfiguration(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  AdminUiController adminUiController(AuthorizationProperties properties) {
    return new AdminUiController(properties);
  }
}
