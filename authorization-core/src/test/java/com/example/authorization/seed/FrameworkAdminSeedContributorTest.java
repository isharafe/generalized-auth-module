package com.example.authorization.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrameworkAdminSeedContributorTest {
  @Test
  void usesTheConfiguredAdminApiBasePathForEveryPermission() {
    AuthorizationSeedBuilder builder = new AuthorizationSeedBuilder();

    new FrameworkAdminSeedContributor("/company/authorization/").contribute(builder);

    assertThat(builder.build().getPermissions())
        .isNotEmpty()
        .allSatisfy(
            permission -> {
              assertThat(permission.pattern()).contains(":/company/authorization");
              assertThat(permission.pattern()).doesNotContain("/authorization-admin/api");
            });
    assertThat(builder.build().getPermissions())
        .anySatisfy(
            permission ->
                assertThat(permission.code()).isEqualTo("AUTHZ_AUTHORIZATION_TEST"));
  }
}
