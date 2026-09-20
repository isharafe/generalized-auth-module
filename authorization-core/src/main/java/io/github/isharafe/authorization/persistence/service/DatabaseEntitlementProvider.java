package io.github.isharafe.authorization.persistence.service;

import io.github.isharafe.authorization.domain.*;
import io.github.isharafe.authorization.engine.AuthorizationInfrastructureException;
import io.github.isharafe.authorization.persistence.entity.*;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.spi.EntitlementProvider;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class DatabaseEntitlementProvider implements EntitlementProvider {
  private final UserRepository users;

  @Override
  @Transactional(readOnly = true)
  public UserEntitlements load(AuthenticatedIdentity identity) {
    try {
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
    } catch (DataAccessException | PersistenceException failure) {
      throw new AuthorizationInfrastructureException(
          "Unable to load entitlements for " + identity.cacheKey(), failure);
    }
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
