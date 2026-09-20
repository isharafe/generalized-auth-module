package io.github.isharafe.authorization.seed;

import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.ExternalAuthorityMappingSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.PermissionGroupSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.PermissionSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.ResourceRuleSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.RoleSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.UserAssignmentSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.UserSeed;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class AuthorizationSeedComposer {
  private final AuthorizationSeedDefinition definition = new AuthorizationSeedDefinition();
  private final Set<String> modulePermissions = new HashSet<>();
  private final Set<String> moduleGroups = new HashSet<>();
  private final Set<String> moduleRoles = new HashSet<>();
  private final Set<String> moduleRules = new HashSet<>();
  private final Set<String> moduleMappings = new HashSet<>();
  private final Set<String> moduleUsers = new HashSet<>();
  private final Set<String> moduleAssignments = new HashSet<>();

  void addModule(String source, AuthorizationSeedDefinition value) {
    validateSource(source, value);
    claim(source, "permission", keys(value.getPermissions(), PermissionSeed::code), modulePermissions);
    claim(
        source,
        "permission group",
        keys(value.getPermissionGroups(), PermissionGroupSeed::code),
        moduleGroups);
    claim(source, "role", keys(value.getRoles(), RoleSeed::code), moduleRoles);
    claim(source, "resource rule", keys(value.getResourceRules(), ResourceRuleSeed::code), moduleRules);
    claim(
        source,
        "external authority mapping",
        keys(value.getExternalAuthorityMappings(), AuthorizationSeedComposer::mappingKey),
        moduleMappings);
    claim(source, "user", keys(value.getUsers(), AuthorizationSeedComposer::userKey), moduleUsers);
    claim(
        source,
        "user assignment",
        keys(value.getUserAssignments(), AuthorizationSeedComposer::assignmentKey),
        moduleAssignments);
    definition.merge(value);
  }

  void addApplication(String source, AuthorizationSeedDefinition value) {
    validateSource(source, value);
    definition.overlay(value);
  }

  AuthorizationSeedDefinition build() {
    return definition;
  }

  private void validateSource(String source, AuthorizationSeedDefinition value) {
    unique(source, "permission", keys(value.getPermissions(), PermissionSeed::code));
    unique(source, "permission group", keys(value.getPermissionGroups(), PermissionGroupSeed::code));
    unique(source, "role", keys(value.getRoles(), RoleSeed::code));
    unique(source, "resource rule", keys(value.getResourceRules(), ResourceRuleSeed::code));
    unique(
        source,
        "external authority mapping",
        keys(value.getExternalAuthorityMappings(), AuthorizationSeedComposer::mappingKey));
    unique(source, "user", keys(value.getUsers(), AuthorizationSeedComposer::userKey));
    unique(
        source,
        "user assignment",
        keys(value.getUserAssignments(), AuthorizationSeedComposer::assignmentKey));
  }

  private void unique(String source, String kind, List<String> keys) {
    Set<String> seen = new HashSet<>();
    for (String key : keys)
      if (key == null || key.isBlank() || !seen.add(key))
        throw new IllegalArgumentException(
            "Duplicate or blank " + kind + " identity in seed " + source + ": " + key);
  }

  private void claim(String source, String kind, List<String> keys, Set<String> claimed) {
    for (String key : keys)
      if (!claimed.add(key))
        throw new IllegalArgumentException(
            "Multiple modules define " + kind + " " + key + "; conflict found in " + source);
  }

  private static <T> List<String> keys(List<T> values, Function<T, String> key) {
    return values.stream().map(key).toList();
  }

  static String mappingKey(ExternalAuthorityMappingSeed value) {
    return String.join(
        "\u0000",
        text(value.sourceSystem()),
        text(value.authorityType()),
        text(value.authority()),
        value.target() == null ? "" : text(value.target().type()),
        value.target() == null ? "" : text(value.target().code()));
  }

  static String userKey(UserSeed value) {
    return text(value.issuer()) + "\u0000" + text(value.subject());
  }

  static String assignmentKey(UserAssignmentSeed value) {
    return String.join(
        "\u0000",
        text(value.issuer()),
        text(value.subject()),
        text(value.targetType()),
        text(value.targetCode()));
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }
}
