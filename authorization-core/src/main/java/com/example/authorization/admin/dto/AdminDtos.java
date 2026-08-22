package com.example.authorization.admin.dto;

import com.example.authorization.domain.AccessMode;
import com.example.authorization.domain.AssignmentSource;
import com.example.authorization.domain.AuthorizationDecision;
import com.example.authorization.domain.AuthorizationReason;
import com.example.authorization.domain.ResourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AdminDtos {
  private static final String CODE_PATTERN = "[A-Za-z0-9][A-Za-z0-9_.-]{0,99}";

  private AdminDtos() {}

  public record Page<T>(
      List<T> content, int page, int size, long totalElements, int totalPages) {
    public Page {
      content = List.copyOf(content);
    }
  }

  public record Role(
      @NotBlank @Pattern(regexp = CODE_PATTERN) String code,
      @NotBlank @Size(max = 255) String name,
      @Size(max = 1000) String description,
      Boolean enabled,
      Set<String> permissionGroups,
      Long version) {
    public Role {
      permissionGroups =
          permissionGroups == null ? Set.of() : Set.copyOf(permissionGroups);
    }
  }

  public record PermissionGroup(
      @NotBlank @Pattern(regexp = CODE_PATTERN) String code,
      @NotBlank @Size(max = 255) String name,
      @Size(max = 1000) String description,
      Boolean enabled,
      Set<String> permissions,
      Long version) {
    public PermissionGroup {
      permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
  }

  public record Permission(
      @NotBlank @Pattern(regexp = CODE_PATTERN) String code,
      @NotBlank @Size(max = 255) String name,
      @Size(max = 1000) String description,
      @NotNull ResourceType resourceType,
      @NotBlank @Size(max = 1000) String pattern,
      Boolean enabled,
      Long version) {}

  public record ResourceRule(
      @NotBlank @Pattern(regexp = CODE_PATTERN) String code,
      @NotNull ResourceType resourceType,
      @NotBlank @Size(max = 1000) String pattern,
      @NotNull AccessMode accessMode,
      Integer priority,
      Boolean enabled,
      Long version) {}

  public record ExternalMapping(
      Long id,
      @NotBlank @Size(max = 50) String sourceSystem,
      @NotBlank @Size(max = 50) String authorityType,
      @NotBlank @Size(max = 1000) String authorityValue,
      @NotBlank @Size(max = 50) String targetType,
      @NotBlank @Pattern(regexp = CODE_PATTERN) String targetCode,
      Boolean enabled,
      Long version) {}

  public record Assignment(String code, AssignmentSource source, String sourceReference) {}

  public record User(
      Long id,
      String issuer,
      String subject,
      String username,
      String email,
      String firstName,
      String lastName,
      boolean enabled,
      String externalDirectoryId,
      Instant lastIdentitySyncAt,
      String identitySyncStatus,
      long entitlementVersion,
      long version,
      Set<Assignment> roles,
      Set<Assignment> permissionGroups) {
    public User {
      roles = Set.copyOf(roles);
      permissionGroups = Set.copyOf(permissionGroups);
    }
  }

  public record PermissionView(
      String code,
      String name,
      String description,
      ResourceType resourceType,
      String pattern,
      boolean enabled) {}

  public record EffectiveEntitlements(
      Identity identity,
      Set<String> roles,
      Set<String> permissionGroups,
      Set<PermissionView> permissions,
      long version,
      Instant loadedAt) {}

  public record Identity(@NotBlank String issuer, @NotBlank String subject, String username) {}

  public record AuthorizationTestRequest(
      @NotNull @Valid Identity identity,
      ResourceType resourceType,
      String method,
      String path,
      String pattern) {}

  public record AuthorizationTestResponse(
      AuthorizationDecision decision,
      AuthorizationReason reason,
      String matchedRule,
      String matchedPermission,
      List<String> assignmentPath) {}

  public record AuditEvent(
      Long id,
      Instant timestamp,
      String eventType,
      String actorIssuer,
      String actorSubject,
      String target,
      String action,
      String correlationId,
      String detailsJson,
      String requestMethod,
      String requestPath,
      String decision,
      String reason,
      String ruleCode,
      String permissionCode) {}

  public record Capabilities(
      String source,
      boolean identitySynchronization,
      boolean externalAuthorityMapping,
      String syncProvider) {}

  public record SyncStatus(
      boolean supported, String provider, String status, Instant updatedAt, String details) {}
}
