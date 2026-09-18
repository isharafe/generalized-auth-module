package io.github.isharafe.authorization.admin.service;

import io.github.isharafe.authorization.admin.api.AdminApiException;
import io.github.isharafe.authorization.admin.dto.AuthorizationDataBundle;
import io.github.isharafe.authorization.admin.dto.AuthorizationDataBundle.AssignmentData;
import io.github.isharafe.authorization.admin.dto.AuthorizationDataBundle.ExternalMappingData;
import io.github.isharafe.authorization.admin.dto.AuthorizationDataBundle.PendingAssignmentData;
import io.github.isharafe.authorization.admin.dto.AuthorizationDataBundle.PermissionData;
import io.github.isharafe.authorization.admin.dto.AuthorizationDataBundle.PermissionGroupData;
import io.github.isharafe.authorization.admin.dto.AuthorizationDataBundle.ResourceRuleData;
import io.github.isharafe.authorization.admin.dto.AuthorizationDataBundle.RoleData;
import io.github.isharafe.authorization.admin.dto.AuthorizationDataBundle.UserData;
import io.github.isharafe.authorization.admin.dto.AuthorizationDataImportResult;
import io.github.isharafe.authorization.domain.AuthorizationChangeAuditEvent;
import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.persistence.entity.ExternalAuthorityMappingEntity;
import io.github.isharafe.authorization.persistence.entity.PendingUserAssignmentEntity;
import io.github.isharafe.authorization.persistence.entity.PermissionEntity;
import io.github.isharafe.authorization.persistence.entity.PermissionGroupEntity;
import io.github.isharafe.authorization.persistence.entity.ResourceRuleEntity;
import io.github.isharafe.authorization.persistence.entity.RoleEntity;
import io.github.isharafe.authorization.persistence.entity.UserEntity;
import io.github.isharafe.authorization.persistence.entity.UserPermissionGroupEntity;
import io.github.isharafe.authorization.persistence.entity.UserRoleEntity;
import io.github.isharafe.authorization.persistence.repository.ExternalAuthorityMappingRepository;
import io.github.isharafe.authorization.persistence.repository.PendingUserAssignmentRepository;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.PermissionRepository;
import io.github.isharafe.authorization.persistence.repository.ResourceRuleRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.persistence.repository.UserPermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.persistence.repository.UserRoleRepository;
import io.github.isharafe.authorization.security.SpringAuthenticationIdentityResolver;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.ExternalAuthorityMappingSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.ExternalAuthorityTargetSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.PermissionGroupSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.PermissionSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.ResourceRuleSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.RoleSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.UserAssignmentSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.UserSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedValidator;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import io.github.isharafe.authorization.spi.PermissionMatcher;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RequiredArgsConstructor
public class AuthorizationDataTransferService {
  private final PermissionRepository permissions;
  private final PermissionGroupRepository groups;
  private final RoleRepository roles;
  private final ResourceRuleRepository rules;
  private final UserRepository users;
  private final UserRoleRepository userRoles;
  private final UserPermissionGroupRepository userGroups;
  private final ExternalAuthorityMappingRepository externalMappings;
  private final PendingUserAssignmentRepository pendingAssignments;
  private final PermissionMatcher matcher;
  private final AuthorizationCacheInvalidator cache;
  private final AuthorizationAuditPublisher audit;
  private final SpringAuthenticationIdentityResolver identityResolver;
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public AuthorizationDataBundle exportData(Authentication authentication) {
    Map<String, List<AssignmentData>> rolesByUser =
        assignmentsByUser(
            userRoles.findAll(),
            value -> identityKey(value.getUser()),
            value ->
                new AssignmentData(
                    value.getRole().getCode(),
                    value.getSource(),
                    value.getSourceReference()));
    Map<String, List<AssignmentData>> groupsByUser =
        assignmentsByUser(
            userGroups.findAll(),
            value -> identityKey(value.getUser()),
            value ->
                new AssignmentData(
                    value.getPermissionGroup().getCode(),
                    value.getSource(),
                    value.getSourceReference()));

    AuthorizationDataBundle bundle =
        new AuthorizationDataBundle(
            AuthorizationDataBundle.CURRENT_FORMAT_VERSION,
            Instant.now(),
            permissions.findAll().stream()
                .sorted(Comparator.comparing(PermissionEntity::getCode))
                .map(this::permissionData)
                .toList(),
            groups.findAll().stream()
                .sorted(Comparator.comparing(PermissionGroupEntity::getCode))
                .map(this::groupData)
                .toList(),
            roles.findAll().stream()
                .sorted(Comparator.comparing(RoleEntity::getCode))
                .map(this::roleData)
                .toList(),
            rules.findAll().stream()
                .sorted(Comparator.comparing(ResourceRuleEntity::getCode))
                .map(this::ruleData)
                .toList(),
            users.findAll().stream()
                .sorted(
                    Comparator.comparing(UserEntity::getIssuer)
                        .thenComparing(UserEntity::getSubject))
                .map(
                    value ->
                        userData(
                            value,
                            rolesByUser.getOrDefault(identityKey(value), List.of()),
                            groupsByUser.getOrDefault(identityKey(value), List.of())))
                .toList(),
            externalMappings.findAll().stream()
                .sorted(
                    Comparator.comparing(ExternalAuthorityMappingEntity::getSourceSystem)
                        .thenComparing(ExternalAuthorityMappingEntity::getAuthorityType)
                        .thenComparing(ExternalAuthorityMappingEntity::getAuthorityValue)
                        .thenComparing(ExternalAuthorityMappingEntity::getTargetType)
                        .thenComparing(ExternalAuthorityMappingEntity::getTargetCode))
                .map(this::externalMappingData)
                .toList(),
            pendingAssignments.findAll().stream()
                .sorted(
                    Comparator.comparing(PendingUserAssignmentEntity::getIssuer)
                        .thenComparing(PendingUserAssignmentEntity::getSubject)
                        .thenComparing(PendingUserAssignmentEntity::getTargetType)
                        .thenComparing(PendingUserAssignmentEntity::getTargetCode)
                        .thenComparing(value -> value.getSource().name()))
                .map(this::pendingAssignmentData)
                .toList());
    publish(authentication, "ADMIN_DATA_EXPORT", "EXPORT", summary(bundle));
    return bundle;
  }

  @Transactional
  public AuthorizationDataImportResult replaceData(
      AuthorizationDataBundle bundle, Authentication authentication) {
    validate(bundle);
    AuthenticatedIdentity actor = resolve(authentication);
    deletePortableData();
    AuthorizationDataImportResult result = importPortableData(bundle);
    afterCommit(
        cache::invalidateAll,
        new AuthorizationChangeAuditEvent(
            "ADMIN_DATA_IMPORT",
            actor == null ? null : actor.issuer(),
            actor == null ? null : actor.subject(),
            "AUTHORIZATION_DATA",
            "REPLACE",
            summary(bundle)));
    return result;
  }

  private void validate(AuthorizationDataBundle bundle) {
    if (bundle.formatVersion() != AuthorizationDataBundle.CURRENT_FORMAT_VERSION)
      throw AdminApiException.validation(
          "Unsupported authorization data format version " + bundle.formatVersion());

    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setPermissions(
        bundle.permissions().stream()
            .map(
                value ->
                    new PermissionSeed(
                        value.code(),
                        value.name(),
                        value.description(),
                        value.resourceType(),
                        value.pattern(),
                        value.enabled()))
            .toList());
    seed.setPermissionGroups(
        bundle.permissionGroups().stream()
            .map(
                value ->
                    new PermissionGroupSeed(
                        value.code(),
                        value.name(),
                        value.description(),
                        value.permissions(),
                        value.enabled()))
            .toList());
    seed.setRoles(
        bundle.roles().stream()
            .map(
                value ->
                    new RoleSeed(
                        value.code(),
                        value.name(),
                        value.description(),
                        value.permissionGroups(),
                        value.enabled()))
            .toList());
    seed.setResourceRules(
        bundle.resourceRules().stream()
            .map(
                value ->
                    new ResourceRuleSeed(
                        value.code(),
                        value.resourceType(),
                        value.pattern(),
                        value.accessMode(),
                        value.priority(),
                        value.enabled()))
            .toList());
    seed.setExternalAuthorityMappings(
        bundle.externalMappings().stream()
            .map(
                value ->
                    new ExternalAuthorityMappingSeed(
                        value.sourceSystem(),
                        value.authorityType(),
                        value.authorityValue(),
                        new ExternalAuthorityTargetSeed(
                            value.targetType(), value.targetCode()),
                        value.enabled()))
            .toList());
    seed.setUsers(
        bundle.users().stream()
            .map(
                value ->
                    new UserSeed(
                        value.issuer(),
                        value.subject(),
                        value.username(),
                        value.email(),
                        value.firstName(),
                        value.lastName(),
                        value.enabled()))
            .toList());

    List<UserAssignmentSeed> assignments = new ArrayList<>();
    for (UserData user : bundle.users()) {
      unique(
          user.roles().stream().map(AssignmentData::targetCode).toList(),
          "role assignment for " + identityKey(user.issuer(), user.subject()));
      unique(
          user.permissionGroups().stream().map(AssignmentData::targetCode).toList(),
          "permission-group assignment for " + identityKey(user.issuer(), user.subject()));
      user.roles()
          .forEach(
              value ->
                  assignments.add(
                      new UserAssignmentSeed(
                          user.issuer(),
                          user.subject(),
                          "ROLE",
                          value.targetCode(),
                          value.source())));
      user.permissionGroups()
          .forEach(
              value ->
                  assignments.add(
                      new UserAssignmentSeed(
                          user.issuer(),
                          user.subject(),
                          "PERMISSION_GROUP",
                          value.targetCode(),
                          value.source())));
    }
    bundle.pendingUserAssignments().stream()
        .map(
            value ->
                new UserAssignmentSeed(
                    value.issuer(),
                    value.subject(),
                    value.targetType(),
                    value.targetCode(),
                    value.source()))
        .forEach(assignments::add);
    seed.setUserAssignments(assignments);

    unique(
        bundle.users().stream()
            .map(value -> identityKey(value.issuer(), value.subject()))
            .toList(),
        "user identity");
    unique(
        bundle.externalMappings().stream()
            .map(
                value ->
                    String.join(
                        "\u0000",
                        value.sourceSystem(),
                        value.authorityType(),
                        value.authorityValue(),
                        value.targetType(),
                        value.targetCode()))
            .toList(),
        "external mapping");
    unique(
        bundle.pendingUserAssignments().stream()
            .map(
                value ->
                    String.join(
                        "\u0000",
                        value.issuer(),
                        value.subject(),
                        value.targetType(),
                        value.targetCode(),
                        value.source().name()))
            .toList(),
        "pending user assignment");
    bundle.permissionGroups()
        .forEach(
            value ->
                unique(
                    value.permissions(),
                    "permission membership for permission group " + value.code()));
    bundle.roles()
        .forEach(
            value ->
                unique(
                    value.permissionGroups(),
                    "permission-group membership for role " + value.code()));
    new AuthorizationSeedValidator(matcher).validate(seed);
  }

  private void deletePortableData() {
    pendingAssignments.deleteAllInBatch();
    userRoles.deleteAllInBatch();
    userGroups.deleteAllInBatch();
    externalMappings.deleteAllInBatch();
    roles.findAll().forEach(value -> value.getPermissionGroups().clear());
    roles.flush();
    groups.findAll().forEach(value -> value.getPermissions().clear());
    groups.flush();
    users.deleteAllInBatch();
    roles.deleteAllInBatch();
    groups.deleteAllInBatch();
    permissions.deleteAllInBatch();
    rules.deleteAllInBatch();
    entityManager.flush();
    entityManager.clear();
  }

  private AuthorizationDataImportResult importPortableData(AuthorizationDataBundle bundle) {
    Map<String, PermissionEntity> permissionByCode = new HashMap<>();
    bundle.permissions().forEach(
        value -> {
          PermissionEntity entity = new PermissionEntity();
          entity.setCode(value.code());
          entity.setName(value.name());
          entity.setDescription(value.description());
          entity.setResourceType(value.resourceType());
          entity.setPattern(value.pattern());
          entity.setEnabled(value.enabled());
          permissionByCode.put(value.code(), entity);
        });
    permissions.saveAll(permissionByCode.values());
    permissions.flush();

    Map<String, PermissionGroupEntity> groupByCode = new HashMap<>();
    bundle.permissionGroups().forEach(
        value -> {
          PermissionGroupEntity entity = new PermissionGroupEntity();
          entity.setCode(value.code());
          entity.setName(value.name());
          entity.setDescription(value.description());
          entity.setEnabled(value.enabled());
          value.permissions().stream()
              .map(permissionByCode::get)
              .forEach(entity.getPermissions()::add);
          groupByCode.put(value.code(), entity);
        });
    groups.saveAll(groupByCode.values());
    groups.flush();

    Map<String, RoleEntity> roleByCode = new HashMap<>();
    bundle.roles().forEach(
        value -> {
          RoleEntity entity = new RoleEntity();
          entity.setCode(value.code());
          entity.setName(value.name());
          entity.setDescription(value.description());
          entity.setEnabled(value.enabled());
          value.permissionGroups().stream()
              .map(groupByCode::get)
              .forEach(entity.getPermissionGroups()::add);
          roleByCode.put(value.code(), entity);
        });
    roles.saveAll(roleByCode.values());
    roles.flush();

    List<ResourceRuleEntity> ruleEntities =
        bundle.resourceRules().stream()
            .map(
                value -> {
                  ResourceRuleEntity entity = new ResourceRuleEntity();
                  entity.setCode(value.code());
                  entity.setResourceType(value.resourceType());
                  entity.setPattern(value.pattern());
                  entity.setAccessMode(value.accessMode());
                  entity.setPriority(value.priority());
                  entity.setEnabled(value.enabled());
                  return entity;
                })
            .toList();
    rules.saveAll(ruleEntities);

    Map<String, UserEntity> userByIdentity = new HashMap<>();
    bundle.users().forEach(
        value -> {
          UserEntity entity = new UserEntity();
          entity.setIssuer(value.issuer());
          entity.setSubject(value.subject());
          entity.setUsername(value.username());
          entity.setEmail(value.email());
          entity.setFirstName(value.firstName());
          entity.setLastName(value.lastName());
          entity.setEnabled(value.enabled());
          entity.setExternalDirectoryId(value.externalDirectoryId());
          entity.setLastIdentitySyncAt(value.lastIdentitySyncAt());
          entity.setIdentitySyncStatus(value.identitySyncStatus());
          userByIdentity.put(identityKey(value.issuer(), value.subject()), entity);
        });
    users.saveAll(userByIdentity.values());
    users.flush();

    List<UserRoleEntity> roleAssignments = new ArrayList<>();
    List<UserPermissionGroupEntity> groupAssignments = new ArrayList<>();
    Set<UserEntity> assignedUsers = new HashSet<>();
    for (UserData value : bundle.users()) {
      UserEntity user = userByIdentity.get(identityKey(value.issuer(), value.subject()));
      value.roles()
          .forEach(
              assignment -> {
                roleAssignments.add(
                    new UserRoleEntity(
                        user,
                        roleByCode.get(assignment.targetCode()),
                        assignment.source(),
                        assignment.sourceReference()));
                assignedUsers.add(user);
              });
      value.permissionGroups()
          .forEach(
              assignment -> {
                groupAssignments.add(
                    new UserPermissionGroupEntity(
                        user,
                        groupByCode.get(assignment.targetCode()),
                        assignment.source(),
                        assignment.sourceReference()));
                assignedUsers.add(user);
              });
    }
    userRoles.saveAll(roleAssignments);
    userGroups.saveAll(groupAssignments);
    assignedUsers.forEach(UserEntity::incrementEntitlementVersion);
    users.saveAll(assignedUsers);

    externalMappings.saveAll(
        bundle.externalMappings().stream()
            .map(
                value -> {
                  ExternalAuthorityMappingEntity entity =
                      new ExternalAuthorityMappingEntity();
                  entity.setSourceSystem(value.sourceSystem());
                  entity.setAuthorityType(value.authorityType());
                  entity.setAuthorityValue(value.authorityValue());
                  entity.setTargetType(value.targetType());
                  entity.setTargetCode(value.targetCode());
                  entity.setEnabled(value.enabled());
                  return entity;
                })
            .toList());
    pendingAssignments.saveAll(
        bundle.pendingUserAssignments().stream()
            .map(
                value -> {
                  PendingUserAssignmentEntity entity =
                      new PendingUserAssignmentEntity();
                  entity.setIssuer(value.issuer());
                  entity.setSubject(value.subject());
                  entity.setTargetType(value.targetType());
                  entity.setTargetCode(value.targetCode());
                  entity.setSource(value.source());
                  return entity;
                })
            .toList());
    entityManager.flush();

    return new AuthorizationDataImportResult(
        bundle.permissions().size(),
        bundle.permissionGroups().size(),
        bundle.roles().size(),
        bundle.resourceRules().size(),
        bundle.users().size(),
        roleAssignments.size(),
        groupAssignments.size(),
        bundle.externalMappings().size(),
        bundle.pendingUserAssignments().size());
  }

  private PermissionData permissionData(PermissionEntity value) {
    return new PermissionData(
        value.getCode(),
        value.getName(),
        value.getDescription(),
        value.getResourceType(),
        value.getPattern(),
        value.isEnabled());
  }

  private PermissionGroupData groupData(PermissionGroupEntity value) {
    return new PermissionGroupData(
        value.getCode(),
        value.getName(),
        value.getDescription(),
        value.isEnabled(),
        value.getPermissions().stream().map(PermissionEntity::getCode).sorted().toList());
  }

  private RoleData roleData(RoleEntity value) {
    return new RoleData(
        value.getCode(),
        value.getName(),
        value.getDescription(),
        value.isEnabled(),
        value.getPermissionGroups().stream()
            .map(PermissionGroupEntity::getCode)
            .sorted()
            .toList());
  }

  private ResourceRuleData ruleData(ResourceRuleEntity value) {
    return new ResourceRuleData(
        value.getCode(),
        value.getResourceType(),
        value.getPattern(),
        value.getAccessMode(),
        value.getPriority(),
        value.isEnabled());
  }

  private UserData userData(
      UserEntity value,
      List<AssignmentData> roleAssignments,
      List<AssignmentData> groupAssignments) {
    return new UserData(
        value.getIssuer(),
        value.getSubject(),
        value.getUsername(),
        value.getEmail(),
        value.getFirstName(),
        value.getLastName(),
        value.isEnabled(),
        value.getExternalDirectoryId(),
        value.getLastIdentitySyncAt(),
        value.getIdentitySyncStatus(),
        roleAssignments,
        groupAssignments);
  }

  private ExternalMappingData externalMappingData(ExternalAuthorityMappingEntity value) {
    return new ExternalMappingData(
        value.getSourceSystem(),
        value.getAuthorityType(),
        value.getAuthorityValue(),
        value.getTargetType(),
        value.getTargetCode(),
        value.isEnabled());
  }

  private PendingAssignmentData pendingAssignmentData(PendingUserAssignmentEntity value) {
    return new PendingAssignmentData(
        value.getIssuer(),
        value.getSubject(),
        value.getTargetType(),
        value.getTargetCode(),
        value.getSource());
  }

  private <T> Map<String, List<AssignmentData>> assignmentsByUser(
      List<T> values,
      Function<T, String> identity,
      Function<T, AssignmentData> mapper) {
    return values.stream()
        .collect(
            Collectors.groupingBy(
                identity,
                Collectors.collectingAndThen(
                    Collectors.mapping(mapper, Collectors.toList()),
                    assignments ->
                        assignments.stream()
                            .sorted(Comparator.comparing(AssignmentData::targetCode))
                            .toList())));
  }

  private void unique(List<String> values, String description) {
    Set<String> unique = new HashSet<>();
    for (String value : values)
      if (!unique.add(value))
        throw AdminApiException.validation(
            "Duplicate " + description + ": " + printable(value));
  }

  private String printable(String value) {
    return value.replace("\u0000", " / ");
  }

  private String identityKey(UserEntity user) {
    return identityKey(user.getIssuer(), user.getSubject());
  }

  private String identityKey(String issuer, String subject) {
    return issuer + "\u0000" + subject;
  }

  private AuthenticatedIdentity resolve(Authentication authentication) {
    try {
      return identityResolver.resolve(authentication);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private void publish(
      Authentication authentication, String eventType, String action, String details) {
    AuthenticatedIdentity actor = resolve(authentication);
    audit.publishChange(
        new AuthorizationChangeAuditEvent(
            eventType,
            actor == null ? null : actor.issuer(),
            actor == null ? null : actor.subject(),
            "AUTHORIZATION_DATA",
            action,
            details));
  }

  private void afterCommit(Runnable invalidation, AuthorizationChangeAuditEvent event) {
    Runnable callback =
        () -> {
          invalidation.run();
          audit.publishChange(event);
        };
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              callback.run();
            }
          });
    else callback.run();
  }

  private String summary(AuthorizationDataBundle bundle) {
    return """
        {"formatVersion":%d,"permissions":%d,"permissionGroups":%d,"roles":%d,"resourceRules":%d,"users":%d,"externalMappings":%d}
        """
        .formatted(
            bundle.formatVersion(),
            bundle.permissions().size(),
            bundle.permissionGroups().size(),
            bundle.roles().size(),
            bundle.resourceRules().size(),
            bundle.users().size(),
            bundle.externalMappings().size())
        .trim();
  }
}
