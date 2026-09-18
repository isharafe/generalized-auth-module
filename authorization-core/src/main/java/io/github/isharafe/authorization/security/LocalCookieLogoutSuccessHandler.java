package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

public final class LocalCookieLogoutSuccessHandler implements LogoutSuccessHandler {
  private final String target;
  private final RedirectStrategy redirects = new DefaultRedirectStrategy();

  public LocalCookieLogoutSuccessHandler(AuthorizationProperties properties) {
    this.target =
        properties
            .getSecurity()
            .getCookieOauth2()
            .getLogout()
            .getPostLogoutRedirectUri();
  }

  @Override
  public void onLogoutSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication)
      throws IOException {
    redirects.sendRedirect(request, response, resolve(request, target));
  }

  static String resolve(HttpServletRequest request, String value) {
    if (!value.contains("{baseUrl}")) return value;
    StringBuilder base =
        new StringBuilder(request.getScheme()).append("://").append(request.getServerName());
    int port = request.getServerPort();
    if (!(request.isSecure() && port == 443) && !(!request.isSecure() && port == 80)) {
      base.append(':').append(port);
    }
    base.append(request.getContextPath());
    return value.replace("{baseUrl}", base.toString());
  }
}
