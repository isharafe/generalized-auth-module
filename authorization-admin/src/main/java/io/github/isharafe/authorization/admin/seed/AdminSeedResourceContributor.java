package io.github.isharafe.authorization.admin.seed;

import io.github.isharafe.authorization.seed.AuthorizationSeedResource;
import io.github.isharafe.authorization.seed.AuthorizationSeedResourceContributor;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class AdminSeedResourceContributor implements AuthorizationSeedResourceContributor {
  static final String LOCATION =
      "classpath:META-INF/authorization/authorization-admin-seed.yml";

  private final String apiBasePath;
  private final String uiBasePath;

  public AdminSeedResourceContributor(String apiBasePath, String uiBasePath) {
    this.apiBasePath = normalized(apiBasePath, "Admin API");
    this.uiBasePath = normalized(uiBasePath, "Admin UI");
  }

  @Override
  public Collection<AuthorizationSeedResource> seedResources() {
    return List.of(
        new AuthorizationSeedResource(
            "authorization-admin",
            LOCATION,
            Map.of("adminApiBasePath", apiBasePath, "adminUiBasePath", uiBasePath)));
  }

  private String normalized(String value, String name) {
    if (value == null || value.isBlank() || !value.startsWith("/"))
      throw new IllegalArgumentException(name + " base path must start with /");
    return value.length() > 1 && value.endsWith("/")
        ? value.substring(0, value.length() - 1)
        : value;
  }
}
