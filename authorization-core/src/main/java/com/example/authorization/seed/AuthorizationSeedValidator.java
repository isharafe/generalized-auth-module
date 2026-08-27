package com.example.authorization.seed;

import com.example.authorization.domain.Permission;
import com.example.authorization.domain.ResourceRule;
import com.example.authorization.domain.ResourceType;
import com.example.authorization.security.DefaultPermissionMatcher;
import com.example.authorization.seed.AuthorizationSeedDefinition.*;
import com.example.authorization.spi.PermissionMatcher;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AuthorizationSeedValidator {
  private final PermissionMatcher matcher;

  public AuthorizationSeedValidator() {
    this(new DefaultPermissionMatcher());
  }

  public AuthorizationSeedValidator(PermissionMatcher matcher) {
    this.matcher = matcher;
  }

  public void validate(AuthorizationSeedDefinition seed) {
    Set<String> permissions =
        unique(seed.getPermissions().stream().map(PermissionSeed::code).toList(), "permission");
    Set<String> groups =
        unique(
            seed.getPermissionGroups().stream().map(PermissionGroupSeed::code).toList(),
            "permission group");
    Set<String> roles = unique(seed.getRoles().stream().map(RoleSeed::code).toList(), "role");
    unique(seed.getResourceRules().stream().map(ResourceRuleSeed::code).toList(), "resource rule");
    for (PermissionSeed value : seed.getPermissions())
      validatePermission(value);
    for (ResourceRuleSeed value : seed.getResourceRules()) validateRule(value);
    for (ExternalAuthorityMappingSeed value : seed.getExternalAuthorityMappings())
      validateExternalAuthorityMapping(value, roles, groups);
    for (PermissionGroupSeed group : seed.getPermissionGroups())
      for (String code : safe(group.permissions()))
        if (!permissions.contains(code))
          fail("Permission group " + group.code() + " references unknown permission " + code);
    for (RoleSeed role : seed.getRoles())
      for (String code : safe(role.permissionGroups()))
        if (!groups.contains(code))
          fail("Role " + role.code() + " references unknown permission group " + code);
    for (UserAssignmentSeed assignment : seed.getUserAssignments()) {
      boolean valid =
          assignment.targetType().equals("ROLE")
              ? roles.contains(assignment.targetCode())
              : assignment.targetType().equals("PERMISSION_GROUP")
                  && groups.contains(assignment.targetCode());
      if (!valid)
        fail(
            "User assignment references unknown target "
                + assignment.targetType()
                + ":"
                + assignment.targetCode());
    }
    checkRuleConflicts(seed.getResourceRules());
  }

  private void validatePermission(PermissionSeed value) {
    try {
      Permission permission =
          new Permission(
              value.code(),
              value.name(),
              value.description(),
              value.type(),
              value.pattern(),
              value.enabled() == null || value.enabled());
      validateResource(permission.resourceType(), permission.pattern());
    } catch (IllegalArgumentException | NullPointerException exception) {
      fail("Invalid permission pattern " + value.pattern() + ": " + exception.getMessage());
    }
  }

  private void validateRule(ResourceRuleSeed value) {
    try {
      ResourceRule rule =
          new ResourceRule(
              value.code(),
              value.type(),
              value.pattern(),
              value.accessMode(),
              value.priority() == null ? 0 : value.priority(),
              value.enabled() == null || value.enabled());
      validateResource(rule.resourceType(), rule.pattern());
    } catch (IllegalArgumentException | NullPointerException exception) {
      fail("Invalid resource rule " + value.code() + ": " + exception.getMessage());
    }
  }

  private void validateResource(ResourceType type, String pattern) {
    matcher.validate(type, pattern);
  }

  private void validateExternalAuthorityMapping(
      ExternalAuthorityMappingSeed value, Set<String> roles, Set<String> groups) {
    if (value.sourceSystem() == null || value.sourceSystem().isBlank())
      fail("External authority mapping source-system is required");
    if (value.authorityType() == null || value.authorityType().isBlank())
      fail("External authority mapping authority-type is required");
    if (value.authority() == null || value.authority().isBlank())
      fail("External authority mapping authority is required");
    if (value.target() == null || value.target().type() == null || value.target().code() == null)
      fail("External authority mapping target is required");
    boolean valid =
        value.target().type().equals("ROLE")
            ? roles.contains(value.target().code())
            : value.target().type().equals("PERMISSION_GROUP")
                && groups.contains(value.target().code());
    if (!valid)
      fail(
          "External authority mapping references unknown target "
              + value.target().type()
              + ":"
              + value.target().code());
  }

  private void checkRuleConflicts(List<ResourceRuleSeed> rules) {
    for (int i = 0; i < rules.size(); i++)
      for (int j = i + 1; j < rules.size(); j++) {
        ResourceRuleSeed a = rules.get(i), b = rules.get(j);
        if (a.type().equals(b.type())
            && a.pattern().equals(b.pattern())
            && value(a.priority(), 0) == value(b.priority(), 0)
            && a.accessMode() != b.accessMode())
          fail("Conflicting resource rules " + a.code() + " and " + b.code());
      }
  }

  private Set<String> unique(List<String> values, String kind) {
    Set<String> result = new HashSet<>();
    for (String value : values)
      if (value == null || value.isBlank() || !result.add(value))
        fail("Duplicate or blank " + kind + " code: " + value);
    return result;
  }

  private static <T> List<T> safe(List<T> value) {
    return value == null ? List.of() : value;
  }

  private static int value(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private static void fail(String message) {
    throw new IllegalArgumentException(message);
  }
}
