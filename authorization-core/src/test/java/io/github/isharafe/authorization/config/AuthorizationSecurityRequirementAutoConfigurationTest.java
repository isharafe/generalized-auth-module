package io.github.isharafe.authorization.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;

class AuthorizationSecurityRequirementAutoConfigurationTest {
  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AuthorizationSecurityRequirementAutoConfiguration.class))
          .withPropertyValues("authorization.security.cookie-oauth2.enabled=false");

  @Test
  void failsFastWhenNeitherCoreNorTheApplicationSuppliesSecurity() {
    contextRunner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .rootCause()
              .hasMessageContaining("application-defined SecurityFilterChain");
        });
  }

  @Test
  void backsOffForAnApplicationDefinedSecurityChain() {
    contextRunner
        .withUserConfiguration(CustomSecurityConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(SecurityFilterChain.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomSecurityConfiguration {
    @Bean
    SecurityFilterChain applicationSecurityFilterChain() {
      return mock(SecurityFilterChain.class);
    }
  }
}
