package io.github.isharafe.authorization.seed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.isharafe.authorization.domain.AccessMode;
import io.github.isharafe.authorization.domain.AssignmentTargetType;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorizationSeedValidatorTest {
  @Test
  void rejectsUnknownReferencesBeforePersistence() {
    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setPermissionGroups(
        List.of(new PermissionGroupSeed("GROUP", "Group", null, List.of("MISSING"), true)));
    assertThatThrownBy(() -> new AuthorizationSeedValidator().validate(seed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown permission");
  }

  @Test
  void rejectsPermissionPatternWithoutHttpMethodPrefix() {
    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setPermissions(
        List.of(
            new PermissionSeed(
                "URL:VIEW", "View", null, ResourceType.URL, "/api/employees/**", true)));
    assertThatThrownBy(() -> new AuthorizationSeedValidator().validate(seed))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void acceptsOpaqueUiPermissionPattern() {
    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setPermissions(
        List.of(
            new PermissionSeed(
                "UI:UI_VIEW", "View UI", null, ResourceType.UI, "employees.edit", true)));

    assertThatCode(() -> new AuthorizationSeedValidator().validate(seed)).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnqualifiedAndMismatchedPermissionCodes() {
    AuthorizationSeedDefinition unqualified = new AuthorizationSeedDefinition();
    unqualified.setPermissions(
        List.of(
            new PermissionSeed(
                "VIEW", "View", null, ResourceType.URL, "GET:/employees/**", true)));
    AuthorizationSeedDefinition mismatched = new AuthorizationSeedDefinition();
    mismatched.setPermissions(
        List.of(
            new PermissionSeed(
                "UI:VIEW", "View", null, ResourceType.URL, "GET:/employees/**", true)));

    assertThatThrownBy(() -> new AuthorizationSeedValidator().validate(unqualified))
        .hasMessageContaining("must start with URL:");
    assertThatThrownBy(() -> new AuthorizationSeedValidator().validate(mismatched))
        .hasMessageContaining("must start with URL:");
  }

  @Test
  void acceptsSameLocalCodeForDifferentResourceTypes() {
    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setPermissions(
        List.of(
            new PermissionSeed(
                "URL:VIEW", "View URL", null, ResourceType.URL, "GET:/employees/**", true),
            new PermissionSeed(
                "UI:VIEW", "View UI", null, ResourceType.UI, "employees.view", true)));
    seed.setPermissionGroups(
        List.of(
            new PermissionGroupSeed(
                "VIEWERS", "Viewers", null, List.of("URL:VIEW", "UI:VIEW"), true)));

    assertThatCode(() -> new AuthorizationSeedValidator().validate(seed)).doesNotThrowAnyException();
  }

  @Test
  void acceptsOpaqueUiResourceRulePattern() {
    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setResourceRules(
        List.of(
            new ResourceRuleSeed(
                "UI_EMPLOYEES",
                ResourceType.UI,
                "employees.edit",
                AccessMode.AUTHORIZED,
                0,
                true)));

    assertThatCode(() -> new AuthorizationSeedValidator().validate(seed)).doesNotThrowAnyException();
  }

  @Test
  void rejectsBlankUiResourceRulePattern() {
    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setResourceRules(
        List.of(
            new ResourceRuleSeed(
                "UI_EMPLOYEES", ResourceType.UI, " ", AccessMode.AUTHORIZED, 0, true)));

    assertThatThrownBy(() -> new AuthorizationSeedValidator().validate(seed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pattern must not be blank");
  }

  @Test
  void rejectsConflictingRules() {
    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setResourceRules(
        List.of(
            new ResourceRuleSeed("ONE", ResourceType.URL, "GET:/api/**", AccessMode.PERMIT_ALL, 0, true),
            new ResourceRuleSeed("TWO", ResourceType.URL, "GET:/api/**", AccessMode.DENY_ALL, 0, true)));
    assertThatThrownBy(() -> new AuthorizationSeedValidator().validate(seed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conflicting");
  }

  @Test
  void acceptsAllKeycloakExternalAuthorityMappingCombinations() {
    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setRoles(List.of(new RoleSeed("ROLE", "Role", null, List.of(), true)));
    seed.setPermissionGroups(
        List.of(new PermissionGroupSeed("GROUP", "Group", null, List.of(), true)));
    seed.setExternalAuthorityMappings(
        List.of(
            mapping("GROUP", "/Finance", "ROLE", "ROLE"),
            mapping("GROUP", "/Employees", "PERMISSION_GROUP", "GROUP"),
            mapping("ROLE", "approver", "ROLE", "ROLE"),
            mapping("ROLE", "viewer", "PERMISSION_GROUP", "GROUP")));

    assertThatCode(() -> new AuthorizationSeedValidator().validate(seed)).doesNotThrowAnyException();
  }

  @Test
  void rejectsExternalAuthorityMappingWithUnknownTarget() {
    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setExternalAuthorityMappings(
        List.of(mapping("GROUP", "/Finance", "ROLE", "MISSING")));

    assertThatThrownBy(() -> new AuthorizationSeedValidator().validate(seed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown target ROLE:MISSING");
  }

  private ExternalAuthorityMappingSeed mapping(
      String authorityType, String authority, String targetType, String targetCode) {
    return new ExternalAuthorityMappingSeed(
        "KEYCLOAK",
        authorityType,
        authority,
        new ExternalAuthorityTargetSeed(AssignmentTargetType.valueOf(targetType), targetCode),
        true);
  }
}
