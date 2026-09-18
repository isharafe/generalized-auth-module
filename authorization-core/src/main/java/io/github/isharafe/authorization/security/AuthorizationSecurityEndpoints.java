package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AuthorizationSecurityEndpoints {
  private final OAuth2CookieRefreshService refresh;

  public AuthorizationSecurityEndpoints(OAuth2CookieRefreshService refresh) {
    this.refresh = refresh;
  }

  @GetMapping("${authorization.security.cookie-oauth2.csrf-endpoint:/authorization/security/csrf}")
  public CsrfResponse csrf(CsrfToken token) {
    return new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
  }

  @PostMapping(
      "${authorization.security.cookie-oauth2.refresh-endpoint:/authorization/security/token/refresh}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void refresh(HttpServletRequest request, HttpServletResponse response) {
    if (!refresh.refresh(request, response)) {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
    }
  }

  public record CsrfResponse(String token, String headerName, String parameterName) {}
}
