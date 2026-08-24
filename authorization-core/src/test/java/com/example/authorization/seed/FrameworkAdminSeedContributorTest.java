package com.example.authorization.seed;

import static org.assertj.core.api.Assertions.assertThat;

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
        .filteredOn(permission -> !permission.code().equals("AUTHZ_ADMIN_UI"))
        .allSatisfy(
            permission -> {
              assertThat(permission.pattern()).contains(":/company/authorization/api");
              assertThat(permission.pattern()).doesNotContain("/authorization-admin/api");
            });
    assertThat(builder.build().getPermissions())
        .anySatisfy(
            permission ->
                assertThat(permission.code()).isEqualTo("AUTHZ_AUTHORIZATION_TEST"));
    assertThat(builder.build().getPermissions())
        .filteredOn(permission -> permission.code().equals("AUTHZ_ADMIN_UI"))
        .singleElement()
        .satisfies(
            permission ->
                assertThat(permission.pattern()).isEqualTo("GET:/company/authorization/ui/**"));
  }
}
