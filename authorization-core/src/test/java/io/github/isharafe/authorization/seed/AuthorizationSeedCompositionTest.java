package io.github.isharafe.authorization.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.isharafe.authorization.domain.ResourceType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class AuthorizationSeedCompositionTest {
  @Test
  void resolvesVariablesInPackagedModuleSeeds() {
    AuthorizationSeedLoader loader = new AuthorizationSeedLoader(new DefaultResourceLoader());

    AuthorizationSeedDefinition seed =
        loader.load(
            new AuthorizationSeedResource(
                "test-module",
                "classpath:authorization/module-seed.yml",
                Map.of("moduleBasePath", "/custom/module")));

    assertThat(seed.getPermissions())
        .singleElement()
        .satisfies(
            permission ->
                assertThat(permission.pattern()).isEqualTo("GET:/custom/module/items/**"));
  }

  @Test
  void applicationDefinitionsOverrideModuleDefaults() {
    AuthorizationSeedComposer composer = new AuthorizationSeedComposer();
    composer.addModule("module", permission("URL:VIEW", "GET:/module/**"));
    composer.addApplication("application", permission("URL:VIEW", "GET:/application/**"));

    assertThat(composer.build().getPermissions())
        .singleElement()
        .satisfies(
            permission -> assertThat(permission.pattern()).isEqualTo("GET:/application/**"));
  }

  @Test
  void rejectsDuplicateDefinitionsWithinOneSource() {
    AuthorizationSeedDefinition duplicate = permission("URL:VIEW", "GET:/one/**");
    duplicate.merge(permission("URL:VIEW", "GET:/two/**"));

    assertThatThrownBy(
            () -> new AuthorizationSeedComposer().addApplication("application", duplicate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate")
        .hasMessageContaining("URL:VIEW");
  }

  @Test
  void rejectsConflictingOwnershipBetweenModules() {
    AuthorizationSeedComposer composer = new AuthorizationSeedComposer();
    composer.addModule("first-module", permission("URL:VIEW", "GET:/one/**"));

    assertThatThrownBy(
            () -> composer.addModule("second-module", permission("URL:VIEW", "GET:/two/**")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Multiple modules define permission URL:VIEW");
  }

  @Test
  void rejectsUnknownModuleSeedVariables() {
    AuthorizationSeedLoader loader = new AuthorizationSeedLoader(new DefaultResourceLoader());

    assertThatThrownBy(
            () ->
                loader.load(
                    new AuthorizationSeedResource(
                        "test-module", "classpath:authorization/module-seed.yml")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown seed variable");
  }

  private AuthorizationSeedDefinition permission(String code, String pattern) {
    AuthorizationSeedBuilder builder = new AuthorizationSeedBuilder();
    builder.permission(code, code, ResourceType.URL, pattern);
    return builder.build();
  }
}
