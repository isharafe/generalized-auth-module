package io.github.isharafe.authorization.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@AutoConfiguration(
    after = AuthorizationAutoConfiguration.class,
    before = SecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HttpSecurity.class)
@ConditionalOnProperty(
    prefix = "authorization",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnProperty(
    prefix = "authorization.security.cookie-oauth2",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class AuthorizationSecurityRequirementAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  SecurityFilterChain authorizationSecurityFilterChainRequired() {
    throw new IllegalStateException(
        "Authorization requires either authorization.security.cookie-oauth2.enabled=true "
            + "or an application-defined SecurityFilterChain");
  }
}
