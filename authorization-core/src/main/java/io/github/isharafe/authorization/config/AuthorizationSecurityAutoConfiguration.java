package io.github.isharafe.authorization.config;

import io.github.isharafe.authorization.security.AuthorizationHttpSecurityCustomizer;
import io.github.isharafe.authorization.security.AuthorizationSecurityChainKind;
import io.github.isharafe.authorization.security.AuthorizationSecurityEndpoints;
import io.github.isharafe.authorization.security.AuthorizationServiceUnavailableHandler;
import io.github.isharafe.authorization.security.AuthorizationTokenCookies;
import io.github.isharafe.authorization.security.CookieOAuth2LoginSuccessHandler;
import io.github.isharafe.authorization.security.CookieOrHeaderBearerTokenResolver;
import io.github.isharafe.authorization.security.DynamicRequestAuthorizationManager;
import io.github.isharafe.authorization.security.LocalCookieLogoutSuccessHandler;
import io.github.isharafe.authorization.security.OAuth2CookieRefreshService;
import io.github.isharafe.authorization.security.OAuth2RefreshTokenRevokingLogoutHandler;
import io.github.isharafe.authorization.security.OidcCookieLogoutSuccessHandler;
import io.github.isharafe.authorization.security.SpringAuthenticationIdentityResolver;
import io.github.isharafe.authorization.spi.IdentitySynchronizationProvider;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

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
    havingValue = "true")
@EnableConfigurationProperties(AuthorizationProperties.class)
public class AuthorizationSecurityAutoConfiguration {
  @Bean
  AuthorizationTokenCookies authorizationTokenCookies(AuthorizationProperties properties) {
    return new AuthorizationTokenCookies(properties);
  }

  @Bean
  @ConditionalOnMissingBean(BearerTokenResolver.class)
  BearerTokenResolver authorizationBearerTokenResolver(AuthorizationProperties properties) {
    return new CookieOrHeaderBearerTokenResolver(
        properties
            .getSecurity()
            .getCookieOauth2()
            .getAccessTokenCookie()
            .getName());
  }

  @Bean
  @ConditionalOnMissingBean
  CookieCsrfTokenRepository authorizationCsrfTokenRepository(
      AuthorizationProperties properties) {
    AuthorizationProperties.CsrfCookie configured =
        properties.getSecurity().getCookieOauth2().getCsrf();
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieName(configured.getName());
    repository.setHeaderName(configured.getHeaderName());
    repository.setCookieCustomizer(
        cookie ->
            cookie
                .secure(configured.isSecure())
                .sameSite(configured.getSameSite())
                .path("/"));
    return repository;
  }

  @Bean
  @ConditionalOnMissingBean
  RestClientRefreshTokenTokenResponseClient authorizationRefreshTokenClient() {
    return new RestClientRefreshTokenTokenResponseClient();
  }

  @Bean
  OAuth2CookieRefreshService authorizationCookieRefreshService(
      AuthorizationProperties properties,
      ClientRegistrationRepository registrations,
      RestClientRefreshTokenTokenResponseClient tokenClient,
      AuthorizationTokenCookies cookies) {
    return new OAuth2CookieRefreshService(properties, registrations, tokenClient, cookies);
  }

  @Bean
  AuthorizationSecurityEndpoints authorizationSecurityEndpoints(
      OAuth2CookieRefreshService refresh) {
    return new AuthorizationSecurityEndpoints(refresh);
  }

  @Bean
  CookieOAuth2LoginSuccessHandler authorizationLoginSuccessHandler(
      AuthorizationProperties properties,
      OAuth2AuthorizedClientRepository clients,
      SpringAuthenticationIdentityResolver resolver,
      IdentitySynchronizationProvider synchronization,
      AuthorizationTokenCookies cookies) {
    return new CookieOAuth2LoginSuccessHandler(
        properties, clients, resolver, synchronization, cookies);
  }

  @Bean
  OAuth2RefreshTokenRevokingLogoutHandler authorizationRefreshTokenRevokingLogoutHandler(
      AuthorizationProperties properties,
      ClientRegistrationRepository registrations,
      AuthorizationTokenCookies cookies) {
    return new OAuth2RefreshTokenRevokingLogoutHandler(properties, registrations, cookies);
  }

  @Bean
  LogoutHandler authorizationCookieLogoutHandler(
      AuthorizationTokenCookies cookies) {
    return (request, response, authentication) -> cookies.clearAll(response);
  }

  @Bean
  LogoutSuccessHandler authorizationLogoutSuccessHandler(
      AuthorizationProperties properties,
      AuthorizationTokenCookies cookies,
      JwtDecoder decoder,
      ClientRegistrationRepository registrations) {
    return properties.getSecurity().getCookieOauth2().getLogout().getMode()
            == AuthorizationProperties.LogoutMode.OIDC
        ? new OidcCookieLogoutSuccessHandler(properties, cookies, decoder, registrations)
        : new LocalCookieLogoutSuccessHandler(properties);
  }

  @Bean
  ApplicationRunner authorizationCookieSecurityGuard(
      AuthorizationProperties properties,
      ClientRegistrationRepository registrations) {
    return args -> validate(properties, registrations);
  }

  static void validate(
      AuthorizationProperties properties, ClientRegistrationRepository registrations) {
    AuthorizationProperties.CookieOauth2 configured =
        properties.getSecurity().getCookieOauth2();
    if (configured.getRegistrationId() == null || configured.getRegistrationId().isBlank())
      throw new IllegalStateException(
          "authorization.security.cookie-oauth2.registration-id is required");
    ClientRegistration registration =
        registrations.findByRegistrationId(configured.getRegistrationId());
    if (registration == null)
      throw new IllegalStateException(
          "No OAuth2 client registration exists with id " + configured.getRegistrationId());
    for (String path :
        new String[] {
          configured.getCsrfEndpoint(),
          configured.getRefreshEndpoint(),
          configured.getLogoutEndpoint(),
          configured.getLoginSuccessUri()
        }) {
      requireApplicationPath(path, "Authorization security paths");
    }
    validateCookie(configured.getAccessTokenCookie(), "access-token-cookie");
    validateCookie(configured.getRefreshTokenCookie(), "refresh-token-cookie");
    validateCookie(configured.getIdTokenCookie(), "id-token-cookie");
    String accessCookie = configured.getAccessTokenCookie().getName();
    String refreshCookie = configured.getRefreshTokenCookie().getName();
    String idCookie = configured.getIdTokenCookie().getName();
    if (accessCookie.equals(refreshCookie)
        || accessCookie.equals(idCookie)
        || refreshCookie.equals(idCookie))
      throw new IllegalStateException("Authorization token cookie names must be unique");
    if (configured.getCsrf().getName() == null || configured.getCsrf().getName().isBlank())
      throw new IllegalStateException("authorization.security.cookie-oauth2.csrf.name is required");
    if (configured.getCsrf().getHeaderName() == null
        || configured.getCsrf().getHeaderName().isBlank())
      throw new IllegalStateException(
          "authorization.security.cookie-oauth2.csrf.header-name is required");
    validateSameSite(
        configured.getCsrf().getSameSite(),
        configured.getCsrf().isSecure(),
        "authorization.security.cookie-oauth2.csrf");
    if (!pathContains(
        configured.getRefreshTokenCookie().getPath(), configured.getRefreshEndpoint()))
      throw new IllegalStateException(
          "The refresh-token cookie path must contain the refresh endpoint");
    if (configured.getLogout().isRevokeRefreshToken()
        && !pathContains(
            configured.getRefreshTokenCookie().getPath(), configured.getLogoutEndpoint()))
      throw new IllegalStateException(
          "Refresh-token revocation requires its cookie path to contain the logout endpoint");
    if (!configured.getIdTokenCookie().getPath().equals(configured.getLogoutEndpoint()))
      throw new IllegalStateException(
          "The ID-token cookie path must equal the logout endpoint");
    if (configured.getLogout().getMode() == AuthorizationProperties.LogoutMode.OIDC) {
      Object endpoint =
          registration
              .getProviderDetails()
              .getConfigurationMetadata()
              .get("end_session_endpoint");
      if (!(endpoint instanceof String value) || value.isBlank())
        throw new IllegalStateException(
            "OIDC logout mode requires provider end_session_endpoint metadata");
    }
  }

  private static void validateCookie(
      AuthorizationProperties.TokenCookie cookie, String property) {
    String prefix = "authorization.security.cookie-oauth2." + property;
    if (cookie.getName() == null || cookie.getName().isBlank())
      throw new IllegalStateException(prefix + ".name is required");
    requireApplicationPath(cookie.getPath(), prefix + ".path");
    validateSameSite(cookie.getSameSite(), cookie.isSecure(), prefix);
  }

  private static void requireApplicationPath(String path, String property) {
    if (path == null || !path.startsWith("/") || path.startsWith("//"))
      throw new IllegalStateException(property + " must start with a single /");
    if (path.contains("?") || path.contains("#"))
      throw new IllegalStateException(property + " must not contain a query or fragment");
  }

  private static void validateSameSite(String sameSite, boolean secure, String property) {
    if (sameSite == null
        || !(sameSite.equalsIgnoreCase("Strict")
            || sameSite.equalsIgnoreCase("Lax")
            || sameSite.equalsIgnoreCase("None")))
      throw new IllegalStateException(property + ".same-site must be Strict, Lax, or None");
    if (sameSite.equalsIgnoreCase("None") && !secure)
      throw new IllegalStateException(property + ".same-site=None requires secure=true");
  }

  private static boolean pathContains(String cookiePath, String endpoint) {
    if (cookiePath == null || endpoint == null) return false;
    String normalized = cookiePath.endsWith("/") ? cookiePath : cookiePath + "/";
    return endpoint.equals(cookiePath) || endpoint.startsWith(normalized);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  static class DefaultSecurityChains {
    @Bean
    @Order(0)
    SecurityFilterChain authorizationOAuth2LoginSecurityFilterChain(
        HttpSecurity http,
        AuthorizationProperties properties,
        CookieOAuth2LoginSuccessHandler successHandler,
        ObjectProvider<AuthorizationHttpSecurityCustomizer> customizers)
        throws Exception {
      apply(customizers, AuthorizationSecurityChainKind.OAUTH2_LOGIN, http);
      return http.securityMatcher("/oauth2/**", "/login/**")
          .csrf(csrf -> csrf.ignoringRequestMatchers("/login/oauth2/code/**"))
          .sessionManagement(
              sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
          .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
          .oauth2Login(oauth -> oauth.successHandler(successHandler))
          .build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain authorizationApplicationSecurityFilterChain(
        HttpSecurity http,
        AuthorizationProperties properties,
        DynamicRequestAuthorizationManager authorization,
        AuthorizationServiceUnavailableHandler deniedHandler,
        BearerTokenResolver bearerTokens,
        CookieCsrfTokenRepository csrfTokens,
        OAuth2RefreshTokenRevokingLogoutHandler revokingLogoutHandler,
        LogoutHandler authorizationCookieLogoutHandler,
        LogoutSuccessHandler logoutSuccessHandler,
        ObjectProvider<AuthorizationHttpSecurityCustomizer> customizers)
        throws Exception {
      AuthorizationProperties.CookieOauth2 configured =
          properties.getSecurity().getCookieOauth2();
      MediaTypeRequestMatcher htmlRequests = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
      htmlRequests.setIgnoredMediaTypes(Set.of(MediaType.ALL));
      AuthenticationEntryPoint login =
          new LoginUrlAuthenticationEntryPoint(
              "/oauth2/authorization/" + configured.getRegistrationId());
      RequestMatcher cookieAuthenticatedUnsafeRequest =
          request ->
              !Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod())
                  && (CookieOrHeaderBearerTokenResolver.cookie(
                              request, configured.getAccessTokenCookie().getName())
                          != null
                      || CookieOrHeaderBearerTokenResolver.cookie(
                              request, configured.getRefreshTokenCookie().getName())
                          != null
                      || CookieOrHeaderBearerTokenResolver.cookie(
                              request, configured.getIdTokenCookie().getName())
                          != null);
      CsrfFilter cookieCsrfFilter = new CsrfFilter(csrfTokens);
      cookieCsrfFilter.setRequireCsrfProtectionMatcher(cookieAuthenticatedUnsafeRequest);
      cookieCsrfFilter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
      cookieCsrfFilter.setAccessDeniedHandler(new AccessDeniedHandlerImpl());

      apply(customizers, AuthorizationSecurityChainKind.APPLICATION, http);
      return http.sessionManagement(
              sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          // Resource-server auto-configuration exempts every resolved bearer token from its
          // built-in CSRF filter. Install the filter explicitly so cookie tokens stay protected.
          .csrf(AbstractHttpConfigurer::disable)
          .addFilterBefore(cookieCsrfFilter, LogoutFilter.class)
          .oauth2ResourceServer(
              resource ->
                  resource
                      .bearerTokenResolver(bearerTokens)
                      .jwt(Customizer.withDefaults()))
          .authorizeHttpRequests(
              requests ->
                  requests
                      .requestMatchers(
                          configured.getCsrfEndpoint(),
                          configured.getRefreshEndpoint(),
                          configured.getLogoutEndpoint())
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
          .logout(
              logout ->
                  logout
                      .logoutUrl(configured.getLogoutEndpoint())
                      .addLogoutHandler(revokingLogoutHandler)
                      .addLogoutHandler(authorizationCookieLogoutHandler)
                      .logoutSuccessHandler(logoutSuccessHandler))
          .build();
    }

    private static void apply(
        ObjectProvider<AuthorizationHttpSecurityCustomizer> customizers,
        AuthorizationSecurityChainKind chain,
        HttpSecurity http)
        throws Exception {
      for (AuthorizationHttpSecurityCustomizer customizer :
          customizers.orderedStream().toList()) {
        if (customizer.supports(chain)) customizer.customize(chain, http);
      }
    }
  }
}
