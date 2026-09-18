package io.github.isharafe.authorization.persistence.service;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.persistence.entity.*;
import io.github.isharafe.authorization.persistence.repository.*;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RequiredArgsConstructor
public class PendingUserAssignmentResolver {
  private final UserRepository users;
  private final RoleRepository roles;
  private final PermissionGroupRepository groups;
  private final UserRoleRepository userRoles;
  private final UserPermissionGroupRepository userGroups;
  private final PendingUserAssignmentRepository pending;
  private final AuthorizationCacheInvalidator cache;

  @Transactional
  public int resolve(AuthenticatedIdentity identity) {
    UserEntity user =
        users.findByIssuerAndSubject(identity.issuer(), identity.subject()).orElse(null);
    if (user == null) return 0;
    int resolved = 0;
    for (PendingUserAssignmentEntity assignment : pending.findAll()) {
      if (!assignment.getIssuer().equals(identity.issuer())
          || !assignment.getSubject().equals(identity.subject())) continue;
      if (assignment.getTargetType().equals("ROLE")) {
        RoleEntity role = roles.findByCode(assignment.getTargetCode()).orElseThrow();
        UserRoleId id = new UserRoleId(user.getId(), role.getId());
        if (!userRoles.existsById(id))
          userRoles.save(new UserRoleEntity(user, role, assignment.getSource(), "pending-seed"));
      } else {
        PermissionGroupEntity group = groups.findByCode(assignment.getTargetCode()).orElseThrow();
        UserPermissionGroupId id = new UserPermissionGroupId(user.getId(), group.getId());
        if (!userGroups.existsById(id))
          userGroups.save(
              new UserPermissionGroupEntity(user, group, assignment.getSource(), "pending-seed"));
      }
      pending.delete(assignment);
      resolved++;
    }
    if (resolved > 0) {
      user.incrementEntitlementVersion();
      users.save(user);
      invalidateAfterCommit(identity);
    }
    return resolved;
  }

  private void invalidateAfterCommit(AuthenticatedIdentity identity) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            cache.invalidateIdentity(identity);
          }
        });
  }
}
