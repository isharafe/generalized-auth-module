package io.github.isharafe.authorization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class CookieOrHeaderBearerTokenResolverTest {
  private final CookieOrHeaderBearerTokenResolver resolver =
      new CookieOrHeaderBearerTokenResolver("ACCESS_TOKEN");

  @Test
  void resolvesTheBearerCookie() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("ACCESS_TOKEN", "from-cookie"));

    assertThat(resolver.resolve(request)).isEqualTo("from-cookie");
  }

  @Test
  void acceptsTheSameTokenFromHeaderAndCookie() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer shared-token");
    request.setCookies(new Cookie("ACCESS_TOKEN", "shared-token"));

    assertThat(resolver.resolve(request)).isEqualTo("shared-token");
  }

  @Test
  void rejectsConflictingHeaderAndCookieTokens() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer from-header");
    request.setCookies(new Cookie("ACCESS_TOKEN", "from-cookie"));

    assertThatThrownBy(() -> resolver.resolve(request))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("Conflicting bearer tokens");
  }
}
