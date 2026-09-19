package io.github.isharafe.authorization.persistence.service;

import io.github.isharafe.authorization.domain.*;
import io.github.isharafe.authorization.persistence.entity.*;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.spi.EntitlementProvider;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

public class DatabaseEntitlementProvider implements EntitlementProvider {
  private final UserRepository users;
  private final AuthorizationObservation observation;

  public DatabaseEntitlementProvider(
      UserRepository users, AuthorizationObservation observation) {
    this.users = users;
    this.observation = observation;
  }

  @Override
  @Transactional(readOnly = true)
  public UserEntitlements load(AuthenticatedIdentity identity) {
    long started = System.nanoTime();
    String result = "success";
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
    } catch (RuntimeException exception) {
      result = "failure";
      throw exception;
    } finally {
      observation.recordPersistenceOperation(
          "entitlements_load", result, Duration.ofNanos(System.nanoTime() - started));
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
