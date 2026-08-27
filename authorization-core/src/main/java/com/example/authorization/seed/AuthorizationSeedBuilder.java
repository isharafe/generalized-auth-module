package com.example.authorization.seed;

import com.example.authorization.domain.AccessMode;
import com.example.authorization.domain.AssignmentSource;
import com.example.authorization.domain.ResourceType;
import com.example.authorization.seed.AuthorizationSeedDefinition.*;
import java.util.List;

public final class AuthorizationSeedBuilder {
  private final AuthorizationSeedDefinition definition = new AuthorizationSeedDefinition();

  public AuthorizationSeedBuilder permission(String code, String name, ResourceType type, String pattern) {
    definition
        .getPermissions()
        .add(new PermissionSeed(code, name, null, type, pattern, true));
    return this;
  }

  public AuthorizationSeedBuilder permissionGroup(String code, String name, String... permissions) {
    definition
        .getPermissionGroups()
        .add(new PermissionGroupSeed(code, name, null, List.of(permissions), true));
    return this;
  }

  public AuthorizationSeedBuilder role(String code, String name, String... groups) {
    definition.getRoles().add(new RoleSeed(code, name, null, List.of(groups), true));
    return this;
  }

  public AuthorizationSeedBuilder rule(
      String code, String method, String pattern, AccessMode mode) {
    definition
        .getResourceRules()
        .add(new ResourceRuleSeed(code, ResourceType.URL, "%s:%s".formatted(method, pattern), mode, 0, true));
    return this;
  }

  public AuthorizationSeedBuilder externalAuthorityMapping(
      String sourceSystem,
      String authorityType,
      String authority,
      String targetType,
      String targetCode) {
    definition
        .getExternalAuthorityMappings()
        .add(
            new ExternalAuthorityMappingSeed(
                sourceSystem,
                authorityType,
                authority,
                new ExternalAuthorityTargetSeed(targetType, targetCode),
                true));
    return this;
  }

  public AuthorizationSeedBuilder user(String issuer, String subject, String username) {
    definition.getUsers().add(new UserSeed(issuer, subject, username, null, null, null, true));
    return this;
  }

  public AuthorizationSeedBuilder assignRole(String issuer, String subject, String role) {
    definition
        .getUserAssignments()
        .add(new UserAssignmentSeed(issuer, subject, "ROLE", role, AssignmentSource.SEED));
    return this;
  }

  public AuthorizationSeedBuilder assignPermissionGroup(
      String issuer, String subject, String group) {
    definition
        .getUserAssignments()
        .add(
            new UserAssignmentSeed(
                issuer, subject, "PERMISSION_GROUP", group, AssignmentSource.SEED));
    return this;
  }

  public AuthorizationSeedDefinition build() {
    return definition;
  }
}
