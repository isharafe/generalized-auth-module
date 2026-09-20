package io.github.isharafe.authorization.admin.service;

import io.github.isharafe.authorization.admin.api.AdminApiException;
import io.github.isharafe.authorization.admin.dto.AdminDtos;
import io.github.isharafe.authorization.domain.PermissionCode;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.persistence.entity.PermissionEntity;
import io.github.isharafe.authorization.persistence.entity.PermissionGroupEntity;
import io.github.isharafe.authorization.persistence.entity.ResourceRuleEntity;
import io.github.isharafe.authorization.persistence.entity.RoleEntity;
import io.github.isharafe.authorization.persistence.entity.UserEntity;
import io.github.isharafe.authorization.persistence.entity.UserRoleEntity;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.PermissionRepository;
import io.github.isharafe.authorization.persistence.repository.ResourceRuleRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.persistence.repository.UserRoleRepository;
import io.github.isharafe.authorization.spi.PermissionMatcher;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class AuthorizationCatalogAdminService {
  private final RoleRepository roles;
  private final PermissionGroupRepository groups;
  private final PermissionRepository permissions;
  private final ResourceRuleRepository rules;
  private final UserRoleRepository userRoles;
  private final PermissionMatcher matcher;
  private final AdminChangePublisher changes;

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

  private void changedAll(String type, String target, String action) {
    changes.all(type, target, action);
  }

  private void changedRules(String type, String target, String action) {
    changes.rules(type, target, action);
  }

}
