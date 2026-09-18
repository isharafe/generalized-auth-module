package io.github.isharafe.authorization.admin.ui;

import io.github.isharafe.authorization.admin.config.AuthorizationAdminProperties;
import io.github.isharafe.authorization.config.AuthorizationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
@RequestMapping("${authorization.admin.ui.base-path:/authorization-admin}")
public class AdminUiController {
  private final AuthorizationAdminProperties properties;
  private final AuthorizationProperties authorization;

  @GetMapping
  public String redirectToIndex() {
    return "redirect:" + normalized(properties.getUi().getBasePath()) + "/";
  }

  @GetMapping(path = "/", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public Resource index() {
    return new ClassPathResource("META-INF/resources/authorization-admin/index.html");
  }

  @GetMapping(path = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public RuntimeConfig runtimeConfig() {
    AuthorizationProperties.CookieOauth2 security =
        authorization.getSecurity().getCookieOauth2();
    String registration = security.getRegistrationId();
    return new RuntimeConfig(
        normalized(properties.getApi().getBasePath()),
        normalized(properties.getUi().getBasePath()),
        security.isEnabled(),
        security.getCsrfEndpoint(),
        security.getRefreshEndpoint(),
        security.getLogoutEndpoint(),
        registration == null ? null : "/oauth2/authorization/" + registration,
        security.getLogout().getMode().name());
  }

  private String normalized(String value) {
    if (value == null || value.isBlank() || !value.startsWith("/"))
      throw new IllegalStateException("Authorization admin base paths must start with /");
    return value.length() > 1 && value.endsWith("/")
        ? value.substring(0, value.length() - 1)
        : value;
  }

  public record RuntimeConfig(
      String apiBasePath,
      String uiBasePath,
      boolean cookieOauth2Enabled,
      String csrfEndpoint,
      String refreshEndpoint,
      String logoutEndpoint,
      String loginUri,
      String logoutMode) {}
}
