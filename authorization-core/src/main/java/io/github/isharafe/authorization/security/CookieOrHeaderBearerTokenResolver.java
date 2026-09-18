package io.github.isharafe.authorization.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

public final class CookieOrHeaderBearerTokenResolver implements BearerTokenResolver {
  private final String cookieName;
  private final DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();

  public CookieOrHeaderBearerTokenResolver(String cookieName) {
    this.cookieName = Objects.requireNonNull(cookieName, "cookieName");
    if (cookieName.isBlank()) throw new IllegalArgumentException("cookieName must not be blank");
  }

  @Override
  public String resolve(HttpServletRequest request) {
    String headerToken = header.resolve(request);
    String cookieToken = cookie(request, cookieName);
    if (headerToken != null && cookieToken != null && !headerToken.equals(cookieToken)) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(
              "invalid_request",
              "Conflicting bearer tokens were supplied in the header and cookie",
              null));
    }
    return headerToken != null ? headerToken : cookieToken;
  }

  public static String cookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) return cookie.getValue();
    }
    return null;
  }
}
