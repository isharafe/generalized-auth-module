package io.github.isharafe.authorization.domain;

import java.time.Instant;
import java.util.Set;

public record UserEntitlements(
    AuthenticatedIdentity identity,
    Set<String> roles,
    Set<String> permissionGroups,
    Set<Permission> permissions,
    long version,
    Instant loadedAt) {
  public UserEntitlements {
    roles = Set.copyOf(roles);
    permissionGroups = Set.copyOf(permissionGroups);
    permissions = Set.copyOf(permissions);
  }
}
