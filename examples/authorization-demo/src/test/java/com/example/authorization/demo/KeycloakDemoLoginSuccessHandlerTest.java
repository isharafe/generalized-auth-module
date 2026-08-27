package com.example.authorization.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.security.SpringAuthenticationIdentityResolver;
import com.example.authorization.spi.IdentitySynchronizationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

class KeycloakDemoLoginSuccessHandlerTest {
  private final IdentitySynchronizationProvider synchronization =
      mock(IdentitySynchronizationProvider.class);
  private final SpringAuthenticationIdentityResolver identityResolver =
      mock(SpringAuthenticationIdentityResolver.class);
  private final Authentication authentication = mock(Authentication.class);
  private final AuthenticatedIdentity identity =
      new AuthenticatedIdentity("https://id.example/realms/demo", "user-1", "alice");

  @Test
  void synchronizesTheAuthenticatedIdentityBeforeRedirectingToTheDemoUi() throws Exception {
    when(identityResolver.resolve(authentication)).thenReturn(identity);
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler().onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

    verify(synchronization).synchronize(identity);
    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getRedirectedUrl()).endsWith("/demo-ui/");
  }

  @Test
  void returnsServiceUnavailableWhenLoginSynchronizationFails() throws Exception {
    when(identityResolver.resolve(authentication)).thenReturn(identity);
    doThrow(new IllegalStateException("Keycloak unavailable"))
        .when(synchronization)
        .synchronize(identity);
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler().onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getErrorMessage()).contains("authorization synchronization failed");
  }

  private KeycloakDemoLoginSuccessHandler handler() {
    return new KeycloakDemoLoginSuccessHandler(synchronization, identityResolver);
  }
}
