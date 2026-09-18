package io.github.isharafe.authorization.nuxtdemo;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import io.github.isharafe.authorization.security.AuthorizationServiceUnavailableHandler;
import io.github.isharafe.authorization.security.DynamicRequestAuthorizationManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
@Profile("test")
class DemoTestSecurityConfiguration {
  @Bean
  SecurityFilterChain testSecurityFilterChain(
      HttpSecurity http,
      AuthorizationProperties properties,
      DynamicRequestAuthorizationManager authorization,
      AuthorizationServiceUnavailableHandler deniedHandler)
  {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(properties.getUiApi().getEndpoint())
                    .authenticated()
                    .anyRequest()
                    .access(authorization))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler(deniedHandler))
        .build();
  }

  private static final class HeaderAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> USERS =
        Set.of("emma", "michael", "olivia");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
        throws ServletException, IOException {
      String user = request.getHeader("X-Demo-User");
      if (user != null
          && USERS.contains(user)
          && SecurityContextHolder.getContext().getAuthentication() == null) {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(user, "N/A", List.of()));
      }
      chain.doFilter(request, response);
    }
  }
}
