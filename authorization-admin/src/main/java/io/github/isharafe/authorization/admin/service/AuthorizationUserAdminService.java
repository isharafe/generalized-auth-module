package io.github.isharafe.authorization.admin.service;

import io.github.isharafe.authorization.admin.api.AdminApiException;
import io.github.isharafe.authorization.admin.dto.AdminDtos;
import io.github.isharafe.authorization.domain.AssignmentSource;
import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.domain.ProtectedResource;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.domain.UserEntitlements;
import io.github.isharafe.authorization.engine.AuthorizationEngine;
import io.github.isharafe.authorization.persistence.entity.PermissionGroupEntity;
import io.github.isharafe.authorization.persistence.entity.RoleEntity;
import io.github.isharafe.authorization.persistence.entity.UserEntity;
import io.github.isharafe.authorization.persistence.entity.UserPermissionGroupEntity;
import io.github.isharafe.authorization.persistence.entity.UserPermissionGroupId;
import io.github.isharafe.authorization.persistence.entity.UserRoleEntity;
import io.github.isharafe.authorization.persistence.entity.UserRoleId;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.persistence.repository.UserPermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.persistence.repository.UserRoleRepository;
import io.github.isharafe.authorization.security.SpringAuthenticationIdentityResolver;
import io.github.isharafe.authorization.spi.EntitlementProvider;
import io.github.isharafe.authorization.spi.PermissionMatcher;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class AuthorizationUserAdminService {
  private final RoleRepository roles;
  private final PermissionGroupRepository groups;
  private final UserRepository users;
  private final UserRoleRepository userRoles;
  private final UserPermissionGroupRepository userGroups;
  private final EntitlementProvider entitlements;
  private final AuthorizationEngine engine;
  private final PermissionMatcher matcher;
  private final SpringAuthenticationIdentityResolver identityResolver;
  private final AdminChangePublisher changes;

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

  private UserEntity requireUser(Long id) {
    return users.findDetailedById(id).orElseThrow(() -> AdminApiException.notFound("user", id));
  }

  private void requireManual(AssignmentSource source) {
    if (source != AssignmentSource.MANUAL)
      throw AdminApiException.conflict(
          "AUTHZ_ASSIGNMENT_SOURCE_CONFLICT",
          "Only MANUAL user assignments can be removed through the admin API");
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

  private String text(String value) {
    return value == null ? "" : value.trim();
  }

  private void changedIdentity(
      UserEntity user, String type, String target, String action) {
    changes.identity(user, type, target, action);
  }
}
