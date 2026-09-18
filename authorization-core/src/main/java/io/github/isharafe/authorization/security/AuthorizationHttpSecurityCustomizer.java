package io.github.isharafe.authorization.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@FunctionalInterface
public interface AuthorizationHttpSecurityCustomizer {
  void customize(AuthorizationSecurityChainKind chain, HttpSecurity http) throws Exception;

  default boolean supports(AuthorizationSecurityChainKind chain) {
    return true;
  }
}
