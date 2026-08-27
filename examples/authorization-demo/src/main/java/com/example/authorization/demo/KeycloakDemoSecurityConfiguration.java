package com.example.authorization.demo;

import com.example.authorization.security.AuthorizationServiceUnavailableHandler;
import com.example.authorization.security.DynamicRequestAuthorizationManager;
import com.example.authorization.security.SpringAuthenticationIdentityResolver;
import com.example.authorization.spi.IdentitySynchronizationProvider;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration(proxyBeanMethods = false)
@Profile("keycloak-demo")
public class KeycloakDemoSecurityConfiguration {
  @Bean
  KeycloakDemoLoginSuccessHandler keycloakDemoLoginSuccessHandler(
      IdentitySynchronizationProvider synchronization,
      SpringAuthenticationIdentityResolver identityResolver) {
    return new KeycloakDemoLoginSuccessHandler(synchronization, identityResolver);
  }

  @Bean
  LogoutSuccessHandler keycloakDemoLogoutSuccessHandler(
      ClientRegistrationRepository registrations) {
    OidcClientInitiatedLogoutSuccessHandler handler =
        new OidcClientInitiatedLogoutSuccessHandler(registrations);
    handler.setPostLogoutRedirectUri("{baseUrl}/demo-ui/signed-out");
    return handler;
  }

  @Bean
  SecurityFilterChain keycloakDemoSecurityFilterChain(
      HttpSecurity http,
      DynamicRequestAuthorizationManager authorization,
      AuthorizationServiceUnavailableHandler deniedHandler,
      KeycloakDemoLoginSuccessHandler loginSuccessHandler,
      LogoutSuccessHandler keycloakDemoLogoutSuccessHandler)
      throws Exception {
    MediaTypeRequestMatcher htmlRequests = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
    htmlRequests.setIgnoredMediaTypes(Set.of(MediaType.ALL));
    AuthenticationEntryPoint login =
        new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/keycloak");

    return http.csrf(csrf -> csrf.disable())
        .oauth2Login(oauth -> oauth.successHandler(loginSuccessHandler))
        .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/oauth2/**", "/login/**", "/error")
                    .permitAll()
                    .anyRequest()
                    .access(authorization))
        .exceptionHandling(
            errors ->
                errors
                    .defaultAuthenticationEntryPointFor(login, htmlRequests)
                    .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        AnyRequestMatcher.INSTANCE)
                    .accessDeniedHandler(deniedHandler))
        .logout(logout -> logout.logoutSuccessHandler(keycloakDemoLogoutSuccessHandler))
        .build();
  }
}
