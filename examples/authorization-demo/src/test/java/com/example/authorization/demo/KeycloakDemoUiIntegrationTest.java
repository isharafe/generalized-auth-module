package com.example.authorization.demo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(
    classes = {
      AuthorizationDemoApplication.class,
      KeycloakDemoUiIntegrationTest.OAuthTestConfiguration.class
    },
    properties = {
      "authorization.source=database",
      "authorization.keycloak.enabled=false",
      "authorization.keycloak.client-secret=test-sync-secret",
      "spring.security.oauth2.client.registration.keycloak.client-secret=test-login-secret"
    })
@AutoConfigureMockMvc
@ActiveProfiles("keycloak-demo")
class KeycloakDemoUiIntegrationTest {
  @Autowired private MockMvc mvc;

  @Test
  void browserRequestsRedirectToKeycloakWhileApiStyleRequestsRemainUnauthorized() throws Exception {
    mvc.perform(get("/demo-ui/").accept(MediaType.TEXT_HTML))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            header().string(
                HttpHeaders.LOCATION, endsWith("/oauth2/authorization/keycloak")));

    mvc.perform(get("/demo-ui/").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void viewerSeesAndCanOpenOnlyPageOne() throws Exception {
    mvc.perform(get("/demo-ui/").with(user("viewer")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Sample page 1")))
        .andExpect(content().string(not(containsString("Sample page 2"))));

    mvc.perform(get("/demo-ui/page-1").with(user("viewer")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("UI:seePage1")));

    mvc.perform(get("/demo-ui/page-2").with(user("viewer")))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerSeesAndCanOpenBothPages() throws Exception {
    mvc.perform(get("/demo-ui/").with(user("manager")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Sample page 1")))
        .andExpect(content().string(containsString("Sample page 2")));

    mvc.perform(get("/demo-ui/page-1").with(user("manager")))
        .andExpect(status().isOk());
    mvc.perform(get("/demo-ui/page-2").with(user("manager")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("UI:seePage2")));
  }

  @Test
  void logoutEndsKeycloakSessionAndReturnsToPublicPage() throws Exception {
    mvc.perform(get("/logout").with(user("viewer")))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            header().string(HttpHeaders.LOCATION, startsWith("https://id.example/logout?")))
        .andExpect(header().string(HttpHeaders.LOCATION, containsString("id_token_hint=")))
        .andExpect(
            header()
                .string(
                    HttpHeaders.LOCATION, containsString("post_logout_redirect_uri=")));

    mvc.perform(get("/demo-ui/signed-out"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Signed out")))
        .andExpect(content().string(containsString("Sign in again")));
  }

  private RequestPostProcessor user(String subject) {
    return oidcLogin()
        .clientRegistration(clientRegistration())
        .idToken(
            token ->
                token
                    .claim("iss", "local")
                    .claim("sub", subject)
                    .claim("preferred_username", subject));
  }

  private static ClientRegistration clientRegistration() {
    return ClientRegistration.withRegistrationId("keycloak")
        .clientId("authorization-demo-web")
        .clientSecret("test")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .scope("openid", "profile")
        .authorizationUri("https://id.example/authorize")
        .tokenUri("https://id.example/token")
        .jwkSetUri("https://id.example/jwks")
        .userInfoUri("https://id.example/userinfo")
        .userNameAttributeName("sub")
        .clientName("Keycloak")
        .providerConfigurationMetadata(Map.of("end_session_endpoint", "https://id.example/logout"))
        .build();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class OAuthTestConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      return new InMemoryClientRegistrationRepository(clientRegistration());
    }

    @Bean
    JwtDecoder jwtDecoder() {
      return token -> {
        throw new UnsupportedOperationException("JWT decoding is not used by this MockMvc test");
      };
    }
  }
}
