package com.example.authorization.seed;

import com.example.authorization.domain.AssignmentSource;
import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.persistence.entity.*;
import com.example.authorization.persistence.repository.*;
import com.example.authorization.persistence.service.PendingUserAssignmentResolver;
import com.example.authorization.seed.AuthorizationSeedDefinition.*;
import com.example.authorization.spi.AuthorizationCacheInvalidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthorizationSeedService {
  private final PermissionRepository permissions;
  private final PermissionGroupRepository groups;
  private final RoleRepository roles;
  private final ResourceRuleRepository rules;
  private final UserRepository users;
  private final UserRoleRepository userRoles;
  private final UserPermissionGroupRepository userGroups;
  private final PendingUserAssignmentRepository pending;
  private final SeedHistoryRepository history;
  private final AuthorizationCacheInvalidator cache;
  private final ObjectProvider<PendingUserAssignmentResolver> pendingResolver;
  private final AuthorizationSeedValidator validator;

  @Transactional
  public void apply(AuthorizationSeedDefinition seed) {
    validator.validate(seed);
    String checksum = checksum(seed);
    if (history.existsBySourceAndChecksumAndStatus("merged", checksum, "APPLIED")) return;
    seed.getPermissions().forEach(this::mergePermission);
    seed.getPermissionGroups().forEach(this::mergeGroup);
    seed.getRoles().forEach(this::mergeRole);
    seed.getResourceRules().forEach(this::mergeRule);
    seed.getUsers().forEach(this::mergeUser);
    seed.getUserAssignments().forEach(this::mergeAssignment);
    history.save(SeedHistoryEntity.applied("merged", checksum));
    invalidateAfterCommit();
  }

  private void mergePermission(PermissionSeed value) {
    PermissionEntity entity = permissions.findByCode(value.code()).orElseGet(PermissionEntity::new);
    entity.setCode(value.code());
    entity.setName(defaultName(value.name(), value.code()));
    entity.setDescription(value.description());
    entity.setResourceType(value.type());
    entity.setPattern(value.pattern());
    entity.setEnabled(enabled(value.enabled()));
    permissions.save(entity);
  }

  private void mergeGroup(PermissionGroupSeed value) {
    PermissionGroupEntity entity =
        groups.findByCode(value.code()).orElseGet(PermissionGroupEntity::new);
    entity.setCode(value.code());
    entity.setName(defaultName(value.name(), value.code()));
    entity.setDescription(value.description());
    entity.setEnabled(enabled(value.enabled()));
    entity.getPermissions().clear();
    safe(value.permissions()).stream()
        .map(code -> permissions.findByCode(code).orElseThrow())
        .forEach(entity.getPermissions()::add);
    groups.save(entity);
  }

  private void mergeRole(RoleSeed value) {
    RoleEntity entity = roles.findByCode(value.code()).orElseGet(RoleEntity::new);
    entity.setCode(value.code());
    entity.setName(defaultName(value.name(), value.code()));
    entity.setDescription(value.description());
    entity.setEnabled(enabled(value.enabled()));
    entity.getPermissionGroups().clear();
    safe(value.permissionGroups()).stream()
        .map(code -> groups.findByCode(code).orElseThrow())
        .forEach(entity.getPermissionGroups()::add);
    roles.save(entity);
  }

  private void mergeRule(ResourceRuleSeed value) {
    ResourceRuleEntity entity = rules.findByCode(value.code()).orElseGet(ResourceRuleEntity::new);
    entity.setCode(value.code());
    entity.setResourceType(value.type());
    entity.setPattern(value.pattern());
    entity.setAccessMode(value.accessMode());
    entity.setPriority(value.priority() == null ? 0 : value.priority());
    entity.setEnabled(enabled(value.enabled()));
    rules.save(entity);
  }

  private void mergeUser(UserSeed value) {
    UserEntity entity =
        users.findByIssuerAndSubject(value.issuer(), value.subject()).orElseGet(UserEntity::new);
    entity.setIssuer(value.issuer());
    entity.setSubject(value.subject());
    entity.setUsername(value.username());
    entity.setEmail(value.email());
    entity.setFirstName(value.firstName());
    entity.setLastName(value.lastName());
    entity.setEnabled(enabled(value.enabled()));
    UserEntity saved = users.save(entity);
    pendingResolver.ifAvailable(
        resolver ->
            resolver.resolve(
                new AuthenticatedIdentity(saved.getIssuer(), saved.getSubject(), saved.getUsername())));
  }

  private void mergeAssignment(UserAssignmentSeed value) {
    UserEntity user = users.findByIssuerAndSubject(value.issuer(), value.subject()).orElse(null);
    AssignmentSource source = value.source() == null ? AssignmentSource.SEED : value.source();
    if (user == null) {
      PendingUserAssignmentEntity entity = new PendingUserAssignmentEntity();
      entity.setIssuer(value.issuer());
      entity.setSubject(value.subject());
      entity.setTargetType(value.targetType());
      entity.setTargetCode(value.targetCode());
      entity.setSource(source);
      pending.save(entity);
      return;
    }
    if (value.targetType().equals("ROLE")) {
      RoleEntity role = roles.findByCode(value.targetCode()).orElseThrow();
      UserRoleId id = new UserRoleId(user.getId(), role.getId());
      if (!userRoles.existsById(id)) userRoles.save(new UserRoleEntity(user, role, source, "seed"));
    } else {
      PermissionGroupEntity group = groups.findByCode(value.targetCode()).orElseThrow();
      UserPermissionGroupId id = new UserPermissionGroupId(user.getId(), group.getId());
      if (!userGroups.existsById(id))
        userGroups.save(new UserPermissionGroupEntity(user, group, source, "seed"));
    }
    user.incrementEntitlementVersion();
    users.save(user);
  }

  private void invalidateAfterCommit() {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      cache.invalidateAll();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            cache.invalidateAll();
          }
        });
  }

  private String checksum(AuthorizationSeedDefinition seed) {
    try {
      byte[] json = new ObjectMapper().writeValueAsBytes(seed);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot checksum seed", exception);
    }
  }

  private static boolean enabled(Boolean value) {
    return value == null || value;
  }

  private static String defaultName(String name, String code) {
    return name == null ? code : name;
  }

  private static <T> List<T> safe(List<T> value) {
    return value == null ? List.of() : value;
  }
}
