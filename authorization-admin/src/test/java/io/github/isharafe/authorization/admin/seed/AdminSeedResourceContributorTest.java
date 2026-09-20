package io.github.isharafe.authorization.admin.seed;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.isharafe.authorization.domain.AccessMode;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition;
import io.github.isharafe.authorization.seed.AuthorizationSeedLoader;
import io.github.isharafe.authorization.seed.AuthorizationSeedResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class AdminSeedResourceContributorTest {
  @Test
  void packagedSeedUsesConfiguredAdminPathsAndDefinesFrameworkRoles() {
    AdminSeedResourceContributor contributor =
        new AdminSeedResourceContributor(
            "/company/authorization/api/", "/company/authorization/ui/");
    AuthorizationSeedResource resource = contributor.seedResources().iterator().next();
    AuthorizationSeedDefinition seed =
        new AuthorizationSeedLoader(new DefaultResourceLoader()).load(resource);

    assertThat(resource.source()).isEqualTo("authorization-admin");
    assertThat(seed.getPermissions())
        .filteredOn(permission -> !permission.code().startsWith("URL:AUTHZ_ADMIN_UI"))
        .allSatisfy(
            permission ->
                assertThat(permission.pattern()).contains(":/company/authorization/api"));
    assertThat(seed.getPermissions())
        .filteredOn(permission -> permission.code().startsWith("URL:AUTHZ_ADMIN_UI"))
        .extracting(permission -> permission.pattern())
        .containsExactlyInAnyOrder(
            "GET:/company/authorization/ui",
            "GET:/company/authorization/ui/",
            "GET:/company/authorization/ui/config",
            "GET:/company/authorization/ui/assets/**");
    assertThat(seed.getRoles())
        .extracting(AuthorizationSeedDefinition.RoleSeed::code)
        .containsExactlyInAnyOrder("AUTHZ_SYSTEM_VIEWER", "AUTHZ_SYSTEM_ADMIN");
    assertThat(seed.getResourceRules())
        .allSatisfy(rule -> assertThat(rule.accessMode()).isEqualTo(AccessMode.AUTHORIZED))
        .extracting(AuthorizationSeedDefinition.ResourceRuleSeed::code)
        .containsExactlyInAnyOrder("AUTHZ_ADMIN_API", "AUTHZ_ADMIN_UI");
  }

  @Test
  void readmeListsEveryFrameworkPermission() throws IOException {
    AuthorizationSeedResource resource =
        new AdminSeedResourceContributor("/authorization-admin/api", "/authorization-admin")
            .seedResources()
            .iterator()
            .next();
    AuthorizationSeedDefinition seed =
        new AuthorizationSeedLoader(new DefaultResourceLoader()).load(resource);
    Set<String> permissionCodes =
        seed.getPermissions().stream()
            .map(AuthorizationSeedDefinition.PermissionSeed::code)
            .collect(Collectors.toSet());
    String readme = Files.readString(Path.of("README.md"));

    assertThat(permissionCodes).allSatisfy(code -> assertThat(readme).contains("`" + code + "`"));
  }
}
