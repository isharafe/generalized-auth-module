package io.github.isharafe.authorization.admin.service;

import io.github.isharafe.authorization.admin.api.AdminApiException;
import io.github.isharafe.authorization.admin.dto.AdminDtos;
import io.github.isharafe.authorization.domain.AuditEventKind;
import io.github.isharafe.authorization.domain.AuthorizationChangeAuditEvent;
import io.github.isharafe.authorization.domain.AssignmentSource;
import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.domain.PermissionCode;
import io.github.isharafe.authorization.domain.ProtectedResource;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.domain.SynchronizationStatus;
import io.github.isharafe.authorization.domain.UserEntitlements;
import io.github.isharafe.authorization.engine.AuthorizationEngine;
import io.github.isharafe.authorization.persistence.entity.AuditEventEntity;
import io.github.isharafe.authorization.persistence.entity.ExternalAuthorityMappingEntity;
import io.github.isharafe.authorization.persistence.entity.PermissionEntity;
import io.github.isharafe.authorization.persistence.entity.PermissionGroupEntity;
import io.github.isharafe.authorization.persistence.entity.ResourceRuleEntity;
import io.github.isharafe.authorization.persistence.entity.RoleEntity;
import io.github.isharafe.authorization.persistence.entity.UserEntity;
import io.github.isharafe.authorization.persistence.entity.UserPermissionGroupEntity;
import io.github.isharafe.authorization.persistence.entity.UserPermissionGroupId;
import io.github.isharafe.authorization.persistence.entity.UserRoleEntity;
import io.github.isharafe.authorization.persistence.entity.UserRoleId;
import io.github.isharafe.authorization.persistence.repository.AuditEventRepository;
import io.github.isharafe.authorization.persistence.repository.ExternalAuthorityMappingRepository;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.PermissionRepository;
import io.github.isharafe.authorization.persistence.repository.ResourceRuleRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.persistence.repository.UserPermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.persistence.repository.UserRoleRepository;
import io.github.isharafe.authorization.security.SpringAuthenticationIdentityResolver;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import io.github.isharafe.authorization.spi.EntitlementProvider;
import io.github.isharafe.authorization.spi.IdentitySynchronizationProvider;
import io.github.isharafe.authorization.spi.PermissionMatcher;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RequiredArgsConstructor
public class AuthorizationAdminService {
  private final RoleRepository roles;
  private final PermissionGroupRepository groups;
  private final PermissionRepository permissions;
  private final ResourceRuleRepository rules;
  private final UserRepository users;
  private final UserRoleRepository userRoles;
  private final UserPermissionGroupRepository userGroups;
  private final ExternalAuthorityMappingRepository externalMappings;
  private final AuditEventRepository auditEvents;
  private final EntitlementProvider entitlements;
  private final AuthorizationEngine engine;
  private final PermissionMatcher matcher;
  private final IdentitySynchronizationProvider synchronization;
  private final AuthorizationCacheInvalidator cache;
  private final AuthorizationAuditPublisher audit;
  private final SpringAuthenticationIdentityResolver identityResolver;

  @Transactional(readOnly = true)
  public AdminDtos.CurrentUser currentUser(Authentication authentication) {
    AuthenticatedIdentity identity = identityResolver.resolve(authentication);
    if (identity == null) {
      throw new AdminApiException(
          HttpStatus.UNAUTHORIZED,
          "AUTHZ_AUTHENTICATION_REQUIRED",
          "An authenticated identity is required");
    }
    UserEntity user = users.findByIssuerAndSubject(identity.issuer(), identity.subject()).orElse(null);
    return new AdminDtos.CurrentUser(
        identity.issuer(),
        identity.subject(),
        user == null || user.getUsername() == null ? identity.username() : user.getUsername(),
        user == null ? null : user.getEmail(),
        user == null ? null : user.getFirstName(),
        user == null ? null : user.getLastName());
  }

  @Transactional(readOnly = true)
  public AdminDtos.Page<AdminDtos.Role> roles(String search, Pageable pageable) {
    return page(roles.search(text(search), pageable), this::role);
  }

  @Transactional(readOnly = true)
  public AdminDtos.Role role(String code) {
    return role(requireRole(code));
  }

  @Transactional
  public AdminDtos.Role createRole(AdminDtos.Role request) {
    if (roles.findByCode(request.code()).isPresent())
      throw AdminApiException.conflict(
          "AUTHZ_ROLE_EXISTS", "Role " + request.code() + " already exists");
    RoleEntity entity = new RoleEntity();
    entity.setCode(request.code());
    updateRole(entity, request, false);
    roles.saveAndFlush(entity);
    changedAll("ADMIN_CREATE", "ROLE:" + entity.getCode(), "CREATE");
    return role(entity);
  }

  @Transactional
  public AdminDtos.Role updateRole(String code, AdminDtos.Role request) {
    requireMatchingCode(code, request.code());
    RoleEntity entity = requireRole(code);
    requireVersion(request.version(), entity.getVersion());
    updateRole(entity, request, true);
    roles.saveAndFlush(entity);
    changedAll("ADMIN_UPDATE", "ROLE:" + code, "UPDATE");
    return role(entity);
  }

  @Transactional
  public void disableRole(String code, Long version) {
    RoleEntity entity = requireRole(code);
    requireVersion(version, entity.getVersion());
    entity.setEnabled(false);
    roles.saveAndFlush(entity);
    changedAll("ADMIN_DISABLE", "ROLE:" + code, "DISABLE");
  }

  @Transactional
  public AdminDtos.Role addRoleGroup(String code, String groupCode) {
    RoleEntity role = requireRole(code);
    PermissionGroupEntity group = requireGroup(groupCode);
    if (role.getPermissionGroups().add(group)) {
      roles.saveAndFlush(role);
      changedAll(
          "ADMIN_MAPPING_ADD",
          "ROLE:" + code,
          "ADD_PERMISSION_GROUP:" + groupCode);
    }
    return role(role);
  }

  @Transactional
  public AdminDtos.Role removeRoleGroup(String code, String groupCode) {
    RoleEntity role = requireRole(code);
    PermissionGroupEntity group = requireGroup(groupCode);
    if (role.getPermissionGroups().remove(group)) {
      roles.saveAndFlush(role);
      changedAll(
          "ADMIN_MAPPING_REMOVE",
          "ROLE:" + code,
          "REMOVE_PERMISSION_GROUP:" + groupCode);
    }
    return role(role);
  }

  @Transactional(readOnly = true)
  public List<AdminDtos.User> roleUsers(String code) {
    RoleEntity role = requireRole(code);
    return userRoles.findByRoleId(role.getId()).stream()
        .map(UserRoleEntity::getUser)
        .map(this::user)
        .sorted(Comparator.comparing(AdminDtos.User::id))
        .toList();
  }

  @Transactional(readOnly = true)
  public AdminDtos.Page<AdminDtos.PermissionGroup> permissionGroups(
      String search, Pageable pageable) {
    return page(groups.search(text(search), pageable), this::group);
  }

  @Transactional(readOnly = true)
  public AdminDtos.PermissionGroup permissionGroup(String code) {
    return group(requireGroup(code));
  }

  @Transactional
  public AdminDtos.PermissionGroup createPermissionGroup(AdminDtos.PermissionGroup request) {
    if (groups.findByCode(request.code()).isPresent())
      throw AdminApiException.conflict(
          "AUTHZ_PERMISSION_GROUP_EXISTS",
          "Permission group " + request.code() + " already exists");
    PermissionGroupEntity entity = new PermissionGroupEntity();
    entity.setCode(request.code());
    updateGroup(entity, request, false);
    groups.saveAndFlush(entity);
    changedAll("ADMIN_CREATE", "PERMISSION_GROUP:" + entity.getCode(), "CREATE");
    return group(entity);
  }

  @Transactional
  public AdminDtos.PermissionGroup updatePermissionGroup(
      String code, AdminDtos.PermissionGroup request) {
    requireMatchingCode(code, request.code());
    PermissionGroupEntity entity = requireGroup(code);
    requireVersion(request.version(), entity.getVersion());
    updateGroup(entity, request, true);
    groups.saveAndFlush(entity);
    changedAll("ADMIN_UPDATE", "PERMISSION_GROUP:" + code, "UPDATE");
    return group(entity);
  }

  @Transactional
  public void disablePermissionGroup(String code, Long version) {
    PermissionGroupEntity entity = requireGroup(code);
    requireVersion(version, entity.getVersion());
    entity.setEnabled(false);
    groups.saveAndFlush(entity);
    changedAll("ADMIN_DISABLE", "PERMISSION_GROUP:" + code, "DISABLE");
  }

  @Transactional
  public AdminDtos.PermissionGroup addGroupPermission(String code, String permissionCode) {
    PermissionGroupEntity group = requireGroup(code);
    PermissionEntity permission = requirePermission(permissionCode);
    if (group.getPermissions().add(permission)) {
      groups.saveAndFlush(group);
      changedAll(
          "ADMIN_MAPPING_ADD",
          "PERMISSION_GROUP:" + code,
          "ADD_PERMISSION:" + permissionCode);
    }
    return group(group);
  }

  @Transactional
  public AdminDtos.PermissionGroup removeGroupPermission(String code, String permissionCode) {
    PermissionGroupEntity group = requireGroup(code);
    PermissionEntity permission = requirePermission(permissionCode);
    if (group.getPermissions().remove(permission)) {
      groups.saveAndFlush(group);
      changedAll(
          "ADMIN_MAPPING_REMOVE",
          "PERMISSION_GROUP:" + code,
          "REMOVE_PERMISSION:" + permissionCode);
    }
    return group(group);
  }

  @Transactional(readOnly = true)
  public AdminDtos.Page<AdminDtos.Permission> permissions(String search, Pageable pageable) {
    return page(permissions.search(text(search), pageable), this::permission);
  }

  @Transactional(readOnly = true)
  public AdminDtos.Permission permission(String code) {
    return permission(requirePermission(code));
  }

  @Transactional
  public AdminDtos.Permission createPermission(AdminDtos.Permission request) {
    PermissionCode.validate(request.code(), request.resourceType());
    if (permissions.findByCode(request.code()).isPresent())
      throw AdminApiException.conflict(
          "AUTHZ_PERMISSION_EXISTS", "Permission " + request.code() + " already exists");
    matcher.validate(request.resourceType(), request.pattern());
    PermissionEntity entity = new PermissionEntity();
    entity.setCode(request.code());
    updatePermission(entity, request, false);
    permissions.saveAndFlush(entity);
    changedAll("ADMIN_CREATE", "PERMISSION:" + entity.getCode(), "CREATE");
    return permission(entity);
  }

  @Transactional
  public AdminDtos.Permission updatePermission(String code, AdminDtos.Permission request) {
    requireMatchingCode(code, request.code());
    PermissionCode.validate(request.code(), request.resourceType());
    PermissionEntity entity = requirePermission(code);
    requireVersion(request.version(), entity.getVersion());
    matcher.validate(request.resourceType(), request.pattern());
    updatePermission(entity, request, true);
    permissions.saveAndFlush(entity);
    changedAll("ADMIN_UPDATE", "PERMISSION:" + code, "UPDATE");
    return permission(entity);
  }

  @Transactional
  public void disablePermission(String code, Long version) {
    PermissionEntity entity = requirePermission(code);
    requireVersion(version, entity.getVersion());
    entity.setEnabled(false);
    permissions.saveAndFlush(entity);
    changedAll("ADMIN_DISABLE", "PERMISSION:" + code, "DISABLE");
  }

  @Transactional(readOnly = true)
  public AdminDtos.Page<AdminDtos.ResourceRule> resourceRules(
      String search, ResourceType resourceType, Pageable pageable) {
    return page(
        resourceType == null
            ? rules.search(text(search), pageable)
            : rules.searchByResourceType(resourceType, text(search), pageable),
        this::rule);
  }

  @Transactional(readOnly = true)
  public AdminDtos.ResourceRule resourceRule(String code) {
    return rule(requireRule(code));
  }

  @Transactional
  public AdminDtos.ResourceRule createResourceRule(AdminDtos.ResourceRule request) {
    if (rules.findByCode(request.code()).isPresent())
      throw AdminApiException.conflict(
          "AUTHZ_RESOURCE_RULE_EXISTS",
          "Resource rule " + request.code() + " already exists");
    validateRule(request, null);
    ResourceRuleEntity entity = new ResourceRuleEntity();
    entity.setCode(request.code());
    updateRule(entity, request, false);
    rules.saveAndFlush(entity);
    changedRules("ADMIN_CREATE", "RESOURCE_RULE:" + entity.getCode(), "CREATE");
    return rule(entity);
  }

  @Transactional
  public AdminDtos.ResourceRule updateResourceRule(
      String code, AdminDtos.ResourceRule request) {
    requireMatchingCode(code, request.code());
    ResourceRuleEntity entity = requireRule(code);
    requireVersion(request.version(), entity.getVersion());
    validateRule(request, code);
    updateRule(entity, request, true);
    rules.saveAndFlush(entity);
    changedRules("ADMIN_UPDATE", "RESOURCE_RULE:" + code, "UPDATE");
    return rule(entity);
  }

  @Transactional
  public void disableResourceRule(String code, Long version) {
    ResourceRuleEntity entity = requireRule(code);
    requireVersion(version, entity.getVersion());
    entity.setEnabled(false);
    rules.saveAndFlush(entity);
    changedRules("ADMIN_DISABLE", "RESOURCE_RULE:" + code, "DISABLE");
  }

  @Transactional(readOnly = true)
  public AdminDtos.Page<AdminDtos.User> users(String search, Pageable pageable) {
    return page(users.search(text(search), pageable), this::user);
  }

  @Transactional(readOnly = true)
  public AdminDtos.User user(Long id) {
    return user(requireUser(id));
  }

  @Transactional
  public AdminDtos.User addUserRole(Long userId, String roleCode) {
    UserEntity user = requireUser(userId);
    RoleEntity role = requireRole(roleCode);
    UserRoleId id = new UserRoleId(userId, role.getId());
    if (!userRoles.existsById(id)) {
      UserRoleEntity assignment =
          userRoles.save(new UserRoleEntity(user, role, AssignmentSource.MANUAL, "admin-api"));
      user.getRoles().add(assignment);
      user.incrementEntitlementVersion();
      users.saveAndFlush(user);
      changedIdentity(
          user,
          "ADMIN_MAPPING_ADD",
          "USER:" + userId,
          "ADD_ROLE:" + roleCode);
    }
    return user(user);
  }

  @Transactional
  public AdminDtos.User removeUserRole(Long userId, String roleCode) {
    UserEntity user = requireUser(userId);
    RoleEntity role = requireRole(roleCode);
    UserRoleId id = new UserRoleId(userId, role.getId());
    UserRoleEntity assignment =
        userRoles.findById(id).orElseThrow(() -> AdminApiException.notFound("user role", roleCode));
    requireManual(assignment.getSource());
    user.getRoles().remove(assignment);
    userRoles.delete(assignment);
    user.incrementEntitlementVersion();
    users.saveAndFlush(user);
    changedIdentity(
        user,
        "ADMIN_MAPPING_REMOVE",
        "USER:" + userId,
        "REMOVE_ROLE:" + roleCode);
    return user(user);
  }

  @Transactional
  public AdminDtos.User addUserPermissionGroup(Long userId, String groupCode) {
    UserEntity user = requireUser(userId);
    PermissionGroupEntity group = requireGroup(groupCode);
    UserPermissionGroupId id = new UserPermissionGroupId(userId, group.getId());
    if (!userGroups.existsById(id)) {
      UserPermissionGroupEntity assignment =
          userGroups.save(
              new UserPermissionGroupEntity(
                  user, group, AssignmentSource.MANUAL, "admin-api"));
      user.getPermissionGroups().add(assignment);
      user.incrementEntitlementVersion();
      users.saveAndFlush(user);
      changedIdentity(
          user,
          "ADMIN_MAPPING_ADD",
          "USER:" + userId,
          "ADD_PERMISSION_GROUP:" + groupCode);
    }
    return user(user);
  }

  @Transactional
  public AdminDtos.User removeUserPermissionGroup(Long userId, String groupCode) {
    UserEntity user = requireUser(userId);
    PermissionGroupEntity group = requireGroup(groupCode);
    UserPermissionGroupId id = new UserPermissionGroupId(userId, group.getId());
    UserPermissionGroupEntity assignment =
        userGroups
            .findById(id)
            .orElseThrow(
                () -> AdminApiException.notFound("user permission group", groupCode));
    requireManual(assignment.getSource());
    user.getPermissionGroups().remove(assignment);
    userGroups.delete(assignment);
    user.incrementEntitlementVersion();
    users.saveAndFlush(user);
    changedIdentity(
        user,
        "ADMIN_MAPPING_REMOVE",
        "USER:" + userId,
        "REMOVE_PERMISSION_GROUP:" + groupCode);
    return user(user);
  }

  @Transactional(readOnly = true)
  public AdminDtos.EffectiveEntitlements effectivePermissions(Long userId) {
    UserEntity user = requireUser(userId);
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity(user.getIssuer(), user.getSubject(), user.getUsername());
    UserEntitlements value = entitlements.load(identity);
    Set<AdminDtos.PermissionView> resolved = new LinkedHashSet<>();
    value.permissions().stream()
        .sorted(Comparator.comparing(io.github.isharafe.authorization.domain.Permission::code))
        .map(
            permission ->
                new AdminDtos.PermissionView(
                    permission.code(),
                    permission.name(),
                    permission.description(),
                    permission.resourceType(),
                    permission.pattern(),
                    permission.enabled()))
        .forEach(resolved::add);
    return new AdminDtos.EffectiveEntitlements(
        new AdminDtos.Identity(identity.issuer(), identity.subject(), identity.username()),
        value.roles(),
        value.permissionGroups(),
        resolved,
        value.version(),
        value.loadedAt());
  }

  @Transactional(readOnly = true)
  public AdminDtos.AuthorizationTestResponse testAuthorization(
      AdminDtos.AuthorizationTestRequest request) {
    ResourceType type =
        request.resourceType() == null ? ResourceType.URL : request.resourceType();
    String pattern;
    if (type == ResourceType.URL) {
      if (request.method() == null
          || request.method().isBlank()
          || request.path() == null
          || request.path().isBlank())
        throw AdminApiException.validation("URL authorization tests require method and path");
      pattern = request.method().toUpperCase(Locale.ROOT) + ":" + request.path();
    } else {
      if (request.pattern() == null || request.pattern().isBlank())
        throw AdminApiException.validation(
            type + " authorization tests require a resource pattern");
      pattern = request.pattern();
    }
    matcher.validate(type, pattern);
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity(
            request.identity().issuer(),
            request.identity().subject(),
            request.identity().username());
    AuthorizationResult result = engine.authorize(new ProtectedResource(type, pattern), identity);
    return new AdminDtos.AuthorizationTestResponse(
        result.decision(),
        result.reason(),
        result.matchedRuleCode(),
        result.matchedPermissionCode(),
        assignmentPath(identity, result.matchedPermissionCode()));
  }

  @Transactional(readOnly = true)
  public AdminDtos.Page<AdminDtos.ExternalMapping> externalMappings(
      String search, Pageable pageable) {
    return page(externalMappings.search(text(search), pageable), this::externalMapping);
  }

  @Transactional(readOnly = true)
  public AdminDtos.ExternalMapping externalMapping(Long id) {
    return externalMapping(requireExternalMapping(id));
  }

  @Transactional
  public AdminDtos.ExternalMapping createExternalMapping(AdminDtos.ExternalMapping request) {
    validateExternalMapping(request);
    ExternalAuthorityMappingEntity entity = new ExternalAuthorityMappingEntity();
    updateExternalMapping(entity, request, false);
    externalMappings.saveAndFlush(entity);
    changedAll(
        "ADMIN_CREATE",
        "EXTERNAL_MAPPING:" + entity.getId(),
        "CREATE");
    return externalMapping(entity);
  }

  @Transactional
  public AdminDtos.ExternalMapping updateExternalMapping(
      Long id, AdminDtos.ExternalMapping request) {
    ExternalAuthorityMappingEntity entity = requireExternalMapping(id);
    requireVersion(request.version(), entity.getVersion());
    validateExternalMapping(request);
    updateExternalMapping(entity, request, true);
    externalMappings.saveAndFlush(entity);
    changedAll("ADMIN_UPDATE", "EXTERNAL_MAPPING:" + id, "UPDATE");
    return externalMapping(entity);
  }

  @Transactional
  public void deleteExternalMapping(Long id, Long version) {
    ExternalAuthorityMappingEntity entity = requireExternalMapping(id);
    requireVersion(version, entity.getVersion());
    externalMappings.delete(entity);
    externalMappings.flush();
    changedAll("ADMIN_DISABLE", "EXTERNAL_MAPPING:" + id, "DELETE");
  }

  @Transactional(readOnly = true)
  public AdminDtos.Page<AdminDtos.AuditEvent> audit(
      AuditEventKind eventKind,
      String eventType,
      String actor,
      String target,
      Pageable pageable) {
    return page(
        auditEvents.search(
            eventKind, nullable(eventType), nullable(actor), nullable(target), pageable),
        this::auditEvent);
  }

  public AdminDtos.SyncStatus syncStatus() {
    SynchronizationStatus status = synchronization.status();
    return new AdminDtos.SyncStatus(
        status.supported(),
        status.provider(),
        status.status(),
        status.updatedAt(),
        status.details());
  }

  public AdminDtos.SyncStatus synchronizeAll() {
    requireSync();
    synchronization.synchronizeAll();
    return syncStatus();
  }

  public AdminDtos.SyncStatus synchronizeIncremental() {
    requireSync();
    synchronization.synchronizeIncremental();
    return syncStatus();
  }

  public AdminDtos.SyncStatus synchronizeIdentity(AuthenticatedIdentity identity) {
    requireSync();
    synchronization.synchronize(identity);
    return syncStatus();
  }

  private void updateRole(RoleEntity entity, AdminDtos.Role request, boolean update) {
    entity.setName(request.name());
    entity.setDescription(request.description());
    entity.setEnabled(request.enabled() == null ? !update || entity.isEnabled() : request.enabled());
    entity.getPermissionGroups().clear();
    for (String code : request.permissionGroups()) entity.getPermissionGroups().add(requireGroup(code));
  }

  private void updateGroup(
      PermissionGroupEntity entity, AdminDtos.PermissionGroup request, boolean update) {
    entity.setName(request.name());
    entity.setDescription(request.description());
    entity.setEnabled(request.enabled() == null ? !update || entity.isEnabled() : request.enabled());
    entity.getPermissions().clear();
    for (String code : request.permissions()) entity.getPermissions().add(requirePermission(code));
  }

  private void updatePermission(
      PermissionEntity entity, AdminDtos.Permission request, boolean update) {
    entity.setName(request.name());
    entity.setDescription(request.description());
    entity.setResourceType(request.resourceType());
    entity.setPattern(request.pattern());
    entity.setEnabled(request.enabled() == null ? !update || entity.isEnabled() : request.enabled());
  }

  private void updateRule(
      ResourceRuleEntity entity, AdminDtos.ResourceRule request, boolean update) {
    entity.setResourceType(request.resourceType());
    entity.setPattern(request.pattern());
    entity.setAccessMode(request.accessMode());
    entity.setPriority(request.priority() == null ? 0 : request.priority());
    entity.setEnabled(request.enabled() == null ? !update || entity.isEnabled() : request.enabled());
  }

  private void updateExternalMapping(
      ExternalAuthorityMappingEntity entity,
      AdminDtos.ExternalMapping request,
      boolean update) {
    entity.setSourceSystem(request.sourceSystem());
    entity.setAuthorityType(request.authorityType());
    entity.setAuthorityValue(request.authorityValue());
    entity.setTargetType(request.targetType().toUpperCase(Locale.ROOT));
    entity.setTargetCode(request.targetCode());
    entity.setEnabled(request.enabled() == null ? !update || entity.isEnabled() : request.enabled());
  }

  private void validateRule(AdminDtos.ResourceRule request, String currentCode) {
    matcher.validate(request.resourceType(), request.pattern());
    if (Boolean.FALSE.equals(request.enabled())) return;
    int priority = request.priority() == null ? 0 : request.priority();
    for (ResourceRuleEntity existing : rules.findByEnabledTrue()) {
      if (existing.getCode().equals(currentCode)) continue;
      if (existing.getResourceType() == request.resourceType()
          && existing.getPattern().equals(request.pattern())
          && existing.getPriority() == priority
          && existing.getAccessMode() != request.accessMode())
        throw AdminApiException.conflict(
            "AUTHZ_RESOURCE_RULE_CONFLICT",
            "Resource rule conflicts with " + existing.getCode());
    }
  }

  private void validateExternalMapping(AdminDtos.ExternalMapping request) {
    String targetType = request.targetType().toUpperCase(Locale.ROOT);
    if ("ROLE".equals(targetType)) requireRole(request.targetCode());
    else if ("PERMISSION_GROUP".equals(targetType)) requireGroup(request.targetCode());
    else
      throw AdminApiException.validation(
          "External mapping targetType must be ROLE or PERMISSION_GROUP");
  }

  private List<String> assignmentPath(
      AuthenticatedIdentity identity, String matchedPermissionCode) {
    if (matchedPermissionCode == null) return List.of();
    UserEntity user =
        users.findByIssuerAndSubjectAndEnabledTrue(identity.issuer(), identity.subject()).orElse(null);
    if (user == null) return List.of();
    for (UserPermissionGroupEntity assignment : user.getPermissionGroups()) {
      PermissionGroupEntity group = assignment.getPermissionGroup();
      if (containsPermission(group, matchedPermissionCode))
        return List.of(
            "USER:" + identity.subject(),
            "PERMISSION_GROUP:" + group.getCode(),
            "PERMISSION:" + matchedPermissionCode);
    }
    for (UserRoleEntity assignment : user.getRoles()) {
      RoleEntity role = assignment.getRole();
      for (PermissionGroupEntity group : role.getPermissionGroups()) {
        if (containsPermission(group, matchedPermissionCode))
          return List.of(
              "USER:" + identity.subject(),
              "ROLE:" + role.getCode(),
              "PERMISSION_GROUP:" + group.getCode(),
              "PERMISSION:" + matchedPermissionCode);
      }
    }
    return List.of("PERMISSION:" + matchedPermissionCode);
  }

  private boolean containsPermission(PermissionGroupEntity group, String code) {
    return group.getPermissions().stream().anyMatch(value -> value.getCode().equals(code));
  }

  private RoleEntity requireRole(String code) {
    return roles.findByCode(code).orElseThrow(() -> AdminApiException.notFound("role", code));
  }

  private PermissionGroupEntity requireGroup(String code) {
    return groups
        .findByCode(code)
        .orElseThrow(() -> AdminApiException.notFound("permission group", code));
  }

  private PermissionEntity requirePermission(String code) {
    return permissions
        .findByCode(code)
        .orElseThrow(() -> AdminApiException.notFound("permission", code));
  }

  private ResourceRuleEntity requireRule(String code) {
    return rules
        .findByCode(code)
        .orElseThrow(() -> AdminApiException.notFound("resource rule", code));
  }

  private UserEntity requireUser(Long id) {
    return users.findDetailedById(id).orElseThrow(() -> AdminApiException.notFound("user", id));
  }

  private ExternalAuthorityMappingEntity requireExternalMapping(Long id) {
    return externalMappings
        .findById(id)
        .orElseThrow(() -> AdminApiException.notFound("external mapping", id));
  }

  private void requireMatchingCode(String pathCode, String requestCode) {
    if (!pathCode.equals(requestCode))
      throw AdminApiException.validation("The resource code is immutable and must match the path");
  }

  private void requireVersion(Long supplied, long current) {
    if (supplied == null)
      throw AdminApiException.validation("version is required for updates and deletes");
    if (supplied != current)
      throw AdminApiException.conflict(
          "AUTHZ_CONCURRENT_MODIFICATION",
          "The resource was changed by another administrator");
  }

  private void requireManual(AssignmentSource source) {
    if (source != AssignmentSource.MANUAL)
      throw AdminApiException.conflict(
          "AUTHZ_ASSIGNMENT_SOURCE_CONFLICT",
          "Only MANUAL user assignments can be removed through the admin API");
  }

  private void requireSync() {
    if (!synchronization.supported())
      throw AdminApiException.unsupported("Identity synchronization is not configured");
  }

  private AdminDtos.Role role(RoleEntity entity) {
    return new AdminDtos.Role(
        entity.getCode(),
        entity.getName(),
        entity.getDescription(),
        entity.isEnabled(),
        sorted(entity.getPermissionGroups().stream().map(PermissionGroupEntity::getCode).toList()),
        entity.getVersion());
  }

  private AdminDtos.PermissionGroup group(PermissionGroupEntity entity) {
    return new AdminDtos.PermissionGroup(
        entity.getCode(),
        entity.getName(),
        entity.getDescription(),
        entity.isEnabled(),
        sorted(entity.getPermissions().stream().map(PermissionEntity::getCode).toList()),
        entity.getVersion());
  }

  private AdminDtos.Permission permission(PermissionEntity entity) {
    return new AdminDtos.Permission(
        entity.getCode(),
        entity.getName(),
        entity.getDescription(),
        entity.getResourceType(),
        entity.getPattern(),
        entity.isEnabled(),
        entity.getVersion());
  }

  private AdminDtos.ResourceRule rule(ResourceRuleEntity entity) {
    return new AdminDtos.ResourceRule(
        entity.getCode(),
        entity.getResourceType(),
        entity.getPattern(),
        entity.getAccessMode(),
        entity.getPriority(),
        entity.isEnabled(),
        entity.getVersion());
  }

  private AdminDtos.ExternalMapping externalMapping(ExternalAuthorityMappingEntity entity) {
    return new AdminDtos.ExternalMapping(
        entity.getId(),
        entity.getSourceSystem(),
        entity.getAuthorityType(),
        entity.getAuthorityValue(),
        entity.getTargetType(),
        entity.getTargetCode(),
        entity.isEnabled(),
        entity.getVersion());
  }

  private AdminDtos.User user(UserEntity entity) {
    Set<AdminDtos.Assignment> roleAssignments = new LinkedHashSet<>();
    entity.getRoles().stream()
        .sorted(Comparator.comparing(value -> value.getRole().getCode()))
        .map(
            value ->
                new AdminDtos.Assignment(
                    value.getRole().getCode(), value.getSource(), value.getSourceReference()))
        .forEach(roleAssignments::add);
    Set<AdminDtos.Assignment> groupAssignments = new LinkedHashSet<>();
    entity.getPermissionGroups().stream()
        .sorted(Comparator.comparing(value -> value.getPermissionGroup().getCode()))
        .map(
            value ->
                new AdminDtos.Assignment(
                    value.getPermissionGroup().getCode(),
                    value.getSource(),
                    value.getSourceReference()))
        .forEach(groupAssignments::add);
    return new AdminDtos.User(
        entity.getId(),
        entity.getIssuer(),
        entity.getSubject(),
        entity.getUsername(),
        entity.getEmail(),
        entity.getFirstName(),
        entity.getLastName(),
        entity.isEnabled(),
        entity.getExternalDirectoryId(),
        entity.getLastIdentitySyncAt(),
        entity.getIdentitySyncStatus(),
        entity.getEntitlementVersion(),
        entity.getVersion(),
        roleAssignments,
        groupAssignments);
  }

  private AdminDtos.AuditEvent auditEvent(AuditEventEntity entity) {
    return new AdminDtos.AuditEvent(
        entity.getId(),
        entity.getTimestamp(),
        entity.getEventKind(),
        entity.getEventType(),
        entity.getActorIssuer(),
        entity.getActorSubject(),
        entity.getTarget(),
        entity.getAction(),
        entity.getCorrelationId(),
        entity.getDetailsJson(),
        entity.getRequestMethod(),
        entity.getRequestPath(),
        entity.getDecision(),
        entity.getReason(),
        entity.getRuleCode(),
        entity.getPermissionCode());
  }

  private <E, D> AdminDtos.Page<D> page(
      org.springframework.data.domain.Page<E> source, Function<E, D> mapper) {
    return new AdminDtos.Page<>(
        source.getContent().stream().map(mapper).toList(),
        source.getNumber(),
        source.getSize(),
        source.getTotalElements(),
        source.getTotalPages());
  }

  private Set<String> sorted(List<String> values) {
    return new TreeSet<>(values);
  }

  private String text(String value) {
    return value == null ? "" : value.trim();
  }

  private String nullable(String value) {
    String normalized = text(value);
    return normalized.isEmpty() ? null : normalized;
  }

  private void changedAll(String type, String target, String action) {
    afterCommit(cache::invalidateAll, changeEvent(type, target, action));
  }

  private void changedRules(String type, String target, String action) {
    afterCommit(cache::invalidateResourceRules, changeEvent(type, target, action));
  }

  private void changedIdentity(
      UserEntity user, String type, String target, String action) {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity(user.getIssuer(), user.getSubject(), user.getUsername());
    afterCommit(() -> cache.invalidateIdentity(identity), changeEvent(type, target, action));
  }

  private AuthorizationChangeAuditEvent changeEvent(String type, String target, String action) {
    AuthenticatedIdentity actor = actor();
    return new AuthorizationChangeAuditEvent(
        type,
        actor == null ? null : actor.issuer(),
        actor == null ? null : actor.subject(),
        target,
        action,
        null);
  }

  private AuthenticatedIdentity actor() {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      return identityResolver.resolve(authentication);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private void afterCommit(Runnable invalidation, AuthorizationChangeAuditEvent event) {
    Runnable callback =
        () -> {
          invalidation.run();
          audit.publishChange(event);
        };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              callback.run();
            }
          });
    } else callback.run();
  }

}
