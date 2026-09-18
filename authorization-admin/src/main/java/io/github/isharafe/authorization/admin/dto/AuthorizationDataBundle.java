package io.github.isharafe.authorization.admin.dto;

import io.github.isharafe.authorization.domain.AccessMode;
import io.github.isharafe.authorization.domain.AssignmentSource;
import io.github.isharafe.authorization.domain.ResourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record AuthorizationDataBundle(
    @NotNull Integer formatVersion,
    @NotNull Instant exportedAt,
    @NotNull List<@NotNull @Valid PermissionData> permissions,
    @NotNull List<@NotNull @Valid PermissionGroupData> permissionGroups,
    @NotNull List<@NotNull @Valid RoleData> roles,
    @NotNull List<@NotNull @Valid ResourceRuleData> resourceRules,
    @NotNull List<@NotNull @Valid UserData> users,
    @NotNull List<@NotNull @Valid ExternalMappingData> externalMappings,
    @NotNull List<@NotNull @Valid PendingAssignmentData> pendingUserAssignments) {
  public static final int CURRENT_FORMAT_VERSION = 1;
  private static final String CODE_PATTERN = "[A-Za-z0-9][A-Za-z0-9_.-]{0,99}";
  private static final String PERMISSION_CODE_PATTERN =
      "[A-Za-z0-9][A-Za-z0-9_.:-]{0,99}";
  private static final String TARGET_TYPE_PATTERN = "ROLE|PERMISSION_GROUP";

  public AuthorizationDataBundle {
    permissions = copy(permissions);
    permissionGroups = copy(permissionGroups);
    roles = copy(roles);
    resourceRules = copy(resourceRules);
    users = copy(users);
    externalMappings = copy(externalMappings);
    pendingUserAssignments = copy(pendingUserAssignments);
  }

  public record PermissionData(
      @NotBlank @Pattern(regexp = PERMISSION_CODE_PATTERN) String code,
      @NotBlank @Size(max = 255) String name,
      @Size(max = 1000) String description,
      @NotNull ResourceType resourceType,
      @NotBlank @Size(max = 1000) String pattern,
      @NotNull Boolean enabled) {}

  public record PermissionGroupData(
      @NotBlank @Pattern(regexp = CODE_PATTERN) String code,
      @NotBlank @Size(max = 255) String name,
      @Size(max = 1000) String description,
      @NotNull Boolean enabled,
      @NotNull List<@NotBlank @Pattern(regexp = PERMISSION_CODE_PATTERN) String> permissions) {
    public PermissionGroupData {
      permissions = copy(permissions);
    }
  }

  public record RoleData(
      @NotBlank @Pattern(regexp = CODE_PATTERN) String code,
      @NotBlank @Size(max = 255) String name,
      @Size(max = 1000) String description,
      @NotNull Boolean enabled,
      @NotNull List<@NotBlank @Pattern(regexp = CODE_PATTERN) String> permissionGroups) {
    public RoleData {
      permissionGroups = copy(permissionGroups);
    }
  }

  public record ResourceRuleData(
      @NotBlank @Pattern(regexp = CODE_PATTERN) String code,
      @NotNull ResourceType resourceType,
      @NotBlank @Size(max = 1000) String pattern,
      @NotNull AccessMode accessMode,
      @NotNull Integer priority,
      @NotNull Boolean enabled) {}

  public record UserData(
      @NotBlank @Size(max = 500) String issuer,
      @NotBlank @Size(max = 500) String subject,
      @Size(max = 255) String username,
      @Size(max = 500) String email,
      @Size(max = 255) String firstName,
      @Size(max = 255) String lastName,
      @NotNull Boolean enabled,
      @Size(max = 500) String externalDirectoryId,
      Instant lastIdentitySyncAt,
      @Size(max = 50) String identitySyncStatus,
      @NotNull List<@NotNull @Valid AssignmentData> roles,
      @NotNull List<@NotNull @Valid AssignmentData> permissionGroups) {
    public UserData {
      roles = copy(roles);
      permissionGroups = copy(permissionGroups);
    }
  }

  public record AssignmentData(
      @NotBlank @Pattern(regexp = CODE_PATTERN) String targetCode,
      @NotNull AssignmentSource source,
      @Size(max = 1000) String sourceReference) {}

  public record ExternalMappingData(
      @NotBlank @Size(max = 50) String sourceSystem,
      @NotBlank @Size(max = 50) String authorityType,
      @NotBlank @Size(max = 1000) String authorityValue,
      @NotBlank @Pattern(regexp = TARGET_TYPE_PATTERN) String targetType,
      @NotBlank @Pattern(regexp = CODE_PATTERN) String targetCode,
      @NotNull Boolean enabled) {}

  public record PendingAssignmentData(
      @NotBlank @Size(max = 500) String issuer,
      @NotBlank @Size(max = 500) String subject,
      @NotBlank @Pattern(regexp = TARGET_TYPE_PATTERN) String targetType,
      @NotBlank @Pattern(regexp = CODE_PATTERN) String targetCode,
      @NotNull AssignmentSource source) {}

  private static <T> List<T> copy(List<T> values) {
    return values == null ? null : List.copyOf(values);
  }
}
