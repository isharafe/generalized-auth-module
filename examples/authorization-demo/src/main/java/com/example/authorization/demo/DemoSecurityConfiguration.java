package com.example.authorization.demo;

import com.example.authorization.security.AuthorizationServiceUnavailableHandler;
import com.example.authorization.security.DynamicRequestAuthorizationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class DemoSecurityConfiguration {
  @Bean
  SecurityFilterChain demoSecurityFilterChain(
      HttpSecurity http,
      DynamicRequestAuthorizationManager authorization,
      AuthorizationServiceUnavailableHandler deniedHandler)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(
            new DemoHeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(requests -> requests.anyRequest().access(authorization))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler(deniedHandler))
        .build();
  }
}
