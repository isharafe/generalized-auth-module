package io.github.isharafe.authorization.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties("authorization.admin")
public class AuthorizationAdminProperties {
  private final Api api = new Api();
  private final Ui ui = new Ui();

  @Getter
  @Setter
  public static class Api {
    private boolean enabled = true;
    private String basePath = "/authorization-admin/api";
  }

  @Getter
  @Setter
  public static class Ui {
    private boolean enabled = true;
    private String basePath = "/authorization-admin";
  }
}
