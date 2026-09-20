package io.github.isharafe.authorization.seed;

import io.github.isharafe.authorization.domain.AccessMode;
import io.github.isharafe.authorization.domain.AssignmentSource;
import io.github.isharafe.authorization.domain.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.Getter;

@Getter
public class AuthorizationSeedDefinition {
  private List<PermissionSeed> permissions = new ArrayList<>();
  private List<PermissionGroupSeed> permissionGroups = new ArrayList<>();
  private List<RoleSeed> roles = new ArrayList<>();
  private List<ResourceRuleSeed> resourceRules = new ArrayList<>();
  private List<ExternalAuthorityMappingSeed> externalAuthorityMappings = new ArrayList<>();
  private List<UserSeed> users = new ArrayList<>();
  private List<UserAssignmentSeed> userAssignments = new ArrayList<>();

  public void setPermissions(List<PermissionSeed> value) {
    permissions = list(value);
  }

  public void setPermissionGroups(List<PermissionGroupSeed> value) {
    permissionGroups = list(value);
  }

  public void setRoles(List<RoleSeed> value) {
    roles = list(value);
  }

  public void setResourceRules(List<ResourceRuleSeed> value) {
    resourceRules = list(value);
  }

  public void setExternalAuthorityMappings(List<ExternalAuthorityMappingSeed> value) {
    externalAuthorityMappings = list(value);
  }

  public void setUsers(List<UserSeed> value) {
    users = list(value);
  }

  public void setUserAssignments(List<UserAssignmentSeed> value) {
    userAssignments = list(value);
  }

  public void merge(AuthorizationSeedDefinition other) {
    permissions.addAll(other.permissions);
    permissionGroups.addAll(other.permissionGroups);
    roles.addAll(other.roles);
    resourceRules.addAll(other.resourceRules);
    externalAuthorityMappings.addAll(other.externalAuthorityMappings);
    users.addAll(other.users);
    userAssignments.addAll(other.userAssignments);
  }

  void overlay(AuthorizationSeedDefinition other) {
    overlay(permissions, other.permissions, PermissionSeed::code);
    overlay(permissionGroups, other.permissionGroups, PermissionGroupSeed::code);
    overlay(roles, other.roles, RoleSeed::code);
    overlay(resourceRules, other.resourceRules, ResourceRuleSeed::code);
    overlay(
        externalAuthorityMappings,
        other.externalAuthorityMappings,
        AuthorizationSeedComposer::mappingKey);
    overlay(users, other.users, AuthorizationSeedComposer::userKey);
    overlay(userAssignments, other.userAssignments, AuthorizationSeedComposer::assignmentKey);
  }

  private static <T> void overlay(List<T> target, List<T> values, Function<T, String> key) {
    for (T value : values) {
      String identity = key.apply(value);
      target.removeIf(existing -> key.apply(existing).equals(identity));
      target.add(value);
    }
  }

  private static <T> List<T> list(List<T> value) {
    return value == null ? new ArrayList<>() : new ArrayList<>(value);
  }

  public record PermissionSeed(
      String code,
      String name,
      String description,
      ResourceType type,
      String pattern,
      Boolean enabled) {}

  public record PermissionGroupSeed(
      String code, String name, String description, List<String> permissions, Boolean enabled) {}

  public record RoleSeed(
      String code,
      String name,
      String description,
      List<String> permissionGroups,
      Boolean enabled) {}

  public record ResourceRuleSeed(
      String code,
      ResourceType type,
      String pattern,
      AccessMode accessMode,
      Integer priority,
      Boolean enabled) {}

  public record ExternalAuthorityMappingSeed(
      String sourceSystem,
      String authorityType,
      String authority,
      ExternalAuthorityTargetSeed target,
      Boolean enabled) {}

  public record ExternalAuthorityTargetSeed(String type, String code) {}

  public record UserSeed(
      String issuer,
      String subject,
      String username,
      String email,
      String firstName,
      String lastName,
      Boolean enabled) {}

  public record UserAssignmentSeed(
      String issuer,
      String subject,
      String targetType,
      String targetCode,
      AssignmentSource source) {}
}
