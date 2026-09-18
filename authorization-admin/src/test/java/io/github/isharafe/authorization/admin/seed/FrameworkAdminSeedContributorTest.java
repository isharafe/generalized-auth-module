package io.github.isharafe.authorization.admin.seed;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.isharafe.authorization.seed.AuthorizationSeedBuilder;
import io.github.isharafe.authorization.domain.Permission;
import io.github.isharafe.authorization.domain.ProtectedResource;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.security.DefaultPermissionMatcher;
import org.junit.jupiter.api.Test;

class FrameworkAdminSeedContributorTest {
  @Test
  void usesTheConfiguredAdminApiAndUiBasePaths() {
    AuthorizationSeedBuilder builder = new AuthorizationSeedBuilder();

    new FrameworkAdminSeedContributor(
            "/company/authorization/api/", "/company/authorization/ui/")
        .contribute(builder);

    assertThat(builder.build().getPermissions())
        .isNotEmpty()
        .filteredOn(permission -> !permission.code().startsWith("URL:AUTHZ_ADMIN_UI"))
        .allSatisfy(
            permission -> {
              assertThat(permission.pattern()).contains(":/company/authorization/api");
              assertThat(permission.pattern()).doesNotContain("/authorization-admin/api");
            });
    assertThat(builder.build().getPermissions())
        .anySatisfy(
            permission ->
                assertThat(permission.code()).isEqualTo("URL:AUTHZ_AUTHORIZATION_TEST"));
    assertThat(builder.build().getPermissions())
        .filteredOn(permission -> permission.code().startsWith("URL:AUTHZ_ADMIN_UI"))
        .extracting(permission -> permission.pattern())
        .containsExactlyInAnyOrder(
            "GET:/company/authorization/ui",
            "GET:/company/authorization/ui/",
            "GET:/company/authorization/ui/config",
            "GET:/company/authorization/ui/assets/**")
        .allSatisfy(pattern -> assertThat(pattern).doesNotContain("/api"));
  }

  @Test
  void uiOnlyPermissionsCannotReadAnyAdminApiEndpoint() {
    AuthorizationSeedBuilder builder = new AuthorizationSeedBuilder();
    new FrameworkAdminSeedContributor().contribute(builder);
    ProtectedResource api =
        new ProtectedResource(ResourceType.URL, "GET:/authorization-admin/api/roles");
    DefaultPermissionMatcher matcher = new DefaultPermissionMatcher();

    assertThat(builder.build().getPermissions())
        .filteredOn(permission -> permission.code().startsWith("URL:AUTHZ_ADMIN_UI"))
        .allSatisfy(
            seed -> {
              Permission permission =
                  new Permission(
                      seed.code(),
                      seed.name(),
                      seed.description(),
                      seed.type(),
                      seed.pattern(),
                      true);
              assertThat(matcher.matches(permission, api)).isFalse();
            });
  }
}
