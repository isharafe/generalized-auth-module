package com.example.authorization.persistence.service;

import com.example.authorization.domain.*;
import com.example.authorization.persistence.entity.*;
import com.example.authorization.persistence.repository.UserRepository;
import com.example.authorization.spi.EntitlementProvider;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DatabaseEntitlementProvider implements EntitlementProvider {
  private final UserRepository users;

  @Override
  @Transactional(readOnly = true)
  public UserEntitlements load(AuthenticatedIdentity identity) {
    UserEntity user =
        users
            .findByIssuerAndSubjectAndEnabledTrue(identity.issuer(), identity.subject())
            .orElse(null);
    if (user == null)
      return new UserEntitlements(identity, Set.of(), Set.of(), Set.of(), 0, Instant.now());
    Set<String> roles = new LinkedHashSet<>();
    Set<String> groups = new LinkedHashSet<>();
    Set<Permission> permissions = new LinkedHashSet<>();
    for (UserRoleEntity assignment : user.getRoles()) {
      RoleEntity role = assignment.getRole();
      if (!role.isEnabled()) continue;
      roles.add(role.getCode());
      for (PermissionGroupEntity group : role.getPermissionGroups())
        collect(group, groups, permissions);
    }
    for (UserPermissionGroupEntity assignment : user.getPermissionGroups()) {
      collect(assignment.getPermissionGroup(), groups, permissions);
    }
    return new UserEntitlements(
        identity, roles, groups, permissions, user.getEntitlementVersion(), Instant.now());
  }

  private void collect(
      PermissionGroupEntity group, Set<String> groups, Set<Permission> permissions) {
    if (!group.isEnabled()) return;
    groups.add(group.getCode());
    group.getPermissions().stream()
        .filter(PermissionEntity::isEnabled)
        .map(PermissionEntity::toDomain)
        .forEach(permissions::add);
  }
}
