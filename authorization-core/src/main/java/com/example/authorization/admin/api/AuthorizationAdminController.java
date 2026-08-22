package com.example.authorization.admin.api;

import com.example.authorization.admin.dto.AdminDtos;
import com.example.authorization.admin.service.AuthorizationAdminService;
import com.example.authorization.domain.AuthenticatedIdentity;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@ConditionalOnProperty(
    prefix = "authorization",
    name = {"enabled", "admin.api.enabled"},
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
@RequestMapping("${authorization.admin.api.base-path:/authorization-admin/api}")
public class AuthorizationAdminController {
  private static final Set<String> CODE_SORTS = Set.of("code", "name", "enabled", "version");
  private final AuthorizationAdminService service;

  @GetMapping("/roles")
  public AdminDtos.Page<AdminDtos.Role> roles(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "code,asc") String sort,
      @RequestParam(defaultValue = "") String search) {
    return service.roles(search, pageable(page, size, sort, "code", CODE_SORTS));
  }

  @PostMapping("/roles")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.Role createRole(@Valid @RequestBody AdminDtos.Role request) {
    return service.createRole(request);
  }

  @GetMapping("/roles/{code}")
  public AdminDtos.Role role(@PathVariable String code) {
    return service.role(code);
  }

  @PutMapping("/roles/{code}")
  public AdminDtos.Role updateRole(
      @PathVariable String code, @Valid @RequestBody AdminDtos.Role request) {
    return service.updateRole(code, request);
  }

  @DeleteMapping("/roles/{code}")
  public ResponseEntity<Void> disableRole(
      @PathVariable String code, @RequestParam Long version) {
    service.disableRole(code, version);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/roles/{code}/permission-groups/{groupCode}")
  public AdminDtos.Role addRoleGroup(
      @PathVariable String code, @PathVariable String groupCode) {
    return service.addRoleGroup(code, groupCode);
  }

  @DeleteMapping("/roles/{code}/permission-groups/{groupCode}")
  public AdminDtos.Role removeRoleGroup(
      @PathVariable String code, @PathVariable String groupCode) {
    return service.removeRoleGroup(code, groupCode);
  }

  @GetMapping("/roles/{code}/users")
  public List<AdminDtos.User> roleUsers(@PathVariable String code) {
    return service.roleUsers(code);
  }

  @GetMapping("/permission-groups")
  public AdminDtos.Page<AdminDtos.PermissionGroup> permissionGroups(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "code,asc") String sort,
      @RequestParam(defaultValue = "") String search) {
    return service.permissionGroups(search, pageable(page, size, sort, "code", CODE_SORTS));
  }

  @PostMapping("/permission-groups")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.PermissionGroup createPermissionGroup(
      @Valid @RequestBody AdminDtos.PermissionGroup request) {
    return service.createPermissionGroup(request);
  }

  @GetMapping("/permission-groups/{code}")
  public AdminDtos.PermissionGroup permissionGroup(@PathVariable String code) {
    return service.permissionGroup(code);
  }

  @PutMapping("/permission-groups/{code}")
  public AdminDtos.PermissionGroup updatePermissionGroup(
      @PathVariable String code, @Valid @RequestBody AdminDtos.PermissionGroup request) {
    return service.updatePermissionGroup(code, request);
  }

  @DeleteMapping("/permission-groups/{code}")
  public ResponseEntity<Void> disablePermissionGroup(
      @PathVariable String code, @RequestParam Long version) {
    service.disablePermissionGroup(code, version);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/permission-groups/{code}/permissions/{permissionCode}")
  public AdminDtos.PermissionGroup addGroupPermission(
      @PathVariable String code, @PathVariable String permissionCode) {
    return service.addGroupPermission(code, permissionCode);
  }

  @DeleteMapping("/permission-groups/{code}/permissions/{permissionCode}")
  public AdminDtos.PermissionGroup removeGroupPermission(
      @PathVariable String code, @PathVariable String permissionCode) {
    return service.removeGroupPermission(code, permissionCode);
  }

  @GetMapping("/permissions")
  public AdminDtos.Page<AdminDtos.Permission> permissions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "code,asc") String sort,
      @RequestParam(defaultValue = "") String search) {
    return service.permissions(search, pageable(page, size, sort, "code", CODE_SORTS));
  }

  @PostMapping("/permissions")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.Permission createPermission(
      @Valid @RequestBody AdminDtos.Permission request) {
    return service.createPermission(request);
  }

  @GetMapping("/permissions/{code}")
  public AdminDtos.Permission permission(@PathVariable String code) {
    return service.permission(code);
  }

  @PutMapping("/permissions/{code}")
  public AdminDtos.Permission updatePermission(
      @PathVariable String code, @Valid @RequestBody AdminDtos.Permission request) {
    return service.updatePermission(code, request);
  }

  @DeleteMapping("/permissions/{code}")
  public ResponseEntity<Void> disablePermission(
      @PathVariable String code, @RequestParam Long version) {
    service.disablePermission(code, version);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/resource-rules")
  public AdminDtos.Page<AdminDtos.ResourceRule> resourceRules(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "code,asc") String sort,
      @RequestParam(defaultValue = "") String search) {
    return service.resourceRules(search, pageable(page, size, sort, "code", CODE_SORTS));
  }

  @PostMapping("/resource-rules")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.ResourceRule createResourceRule(
      @Valid @RequestBody AdminDtos.ResourceRule request) {
    return service.createResourceRule(request);
  }

  @GetMapping("/resource-rules/{code}")
  public AdminDtos.ResourceRule resourceRule(@PathVariable String code) {
    return service.resourceRule(code);
  }

  @PutMapping("/resource-rules/{code}")
  public AdminDtos.ResourceRule updateResourceRule(
      @PathVariable String code, @Valid @RequestBody AdminDtos.ResourceRule request) {
    return service.updateResourceRule(code, request);
  }

  @DeleteMapping("/resource-rules/{code}")
  public ResponseEntity<Void> disableResourceRule(
      @PathVariable String code, @RequestParam Long version) {
    service.disableResourceRule(code, version);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/users")
  public AdminDtos.Page<AdminDtos.User> users(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "id,asc") String sort,
      @RequestParam(defaultValue = "") String search) {
    return service.users(
        search,
        pageable(
            page,
            size,
            sort,
            "id",
            Set.of("id", "issuer", "subject", "username", "email", "enabled", "version")));
  }

  @GetMapping("/users/{id}")
  public AdminDtos.User user(@PathVariable Long id) {
    return service.user(id);
  }

  @PutMapping("/users/{id}/roles/{roleCode}")
  public AdminDtos.User addUserRole(@PathVariable Long id, @PathVariable String roleCode) {
    return service.addUserRole(id, roleCode);
  }

  @DeleteMapping("/users/{id}/roles/{roleCode}")
  public AdminDtos.User removeUserRole(@PathVariable Long id, @PathVariable String roleCode) {
    return service.removeUserRole(id, roleCode);
  }

  @PutMapping("/users/{id}/permission-groups/{groupCode}")
  public AdminDtos.User addUserPermissionGroup(
      @PathVariable Long id, @PathVariable String groupCode) {
    return service.addUserPermissionGroup(id, groupCode);
  }

  @DeleteMapping("/users/{id}/permission-groups/{groupCode}")
  public AdminDtos.User removeUserPermissionGroup(
      @PathVariable Long id, @PathVariable String groupCode) {
    return service.removeUserPermissionGroup(id, groupCode);
  }

  @GetMapping("/users/{id}/effective-permissions")
  public AdminDtos.EffectiveEntitlements effectivePermissions(@PathVariable Long id) {
    return service.effectivePermissions(id);
  }

  @PostMapping("/authorization-test")
  public AdminDtos.AuthorizationTestResponse testAuthorization(
      @Valid @RequestBody AdminDtos.AuthorizationTestRequest request) {
    return service.testAuthorization(request);
  }

  @GetMapping("/external-mappings")
  public AdminDtos.Page<AdminDtos.ExternalMapping> externalMappings(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "id,asc") String sort,
      @RequestParam(defaultValue = "") String search) {
    return service.externalMappings(
        search,
        pageable(
            page,
            size,
            sort,
            "id",
            Set.of(
                "id",
                "sourceSystem",
                "authorityType",
                "authorityValue",
                "targetType",
                "targetCode",
                "enabled",
                "version")));
  }

  @PostMapping("/external-mappings")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.ExternalMapping createExternalMapping(
      @Valid @RequestBody AdminDtos.ExternalMapping request) {
    return service.createExternalMapping(request);
  }

  @GetMapping("/external-mappings/{id}")
  public AdminDtos.ExternalMapping externalMapping(@PathVariable Long id) {
    return service.externalMapping(id);
  }

  @PutMapping("/external-mappings/{id}")
  public AdminDtos.ExternalMapping updateExternalMapping(
      @PathVariable Long id, @Valid @RequestBody AdminDtos.ExternalMapping request) {
    return service.updateExternalMapping(id, request);
  }

  @DeleteMapping("/external-mappings/{id}")
  public ResponseEntity<Void> deleteExternalMapping(
      @PathVariable Long id, @RequestParam Long version) {
    service.deleteExternalMapping(id, version);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/audit")
  public AdminDtos.Page<AdminDtos.AuditEvent> audit(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "timestamp,desc") String sort,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String target) {
    return service.audit(
        eventType,
        actor,
        target,
        pageable(
            page,
            size,
            sort,
            "timestamp",
            Set.of("id", "timestamp", "eventType", "actorIssuer", "actorSubject", "target")));
  }

  @GetMapping("/sync/status")
  public AdminDtos.SyncStatus syncStatus() {
    return service.syncStatus();
  }

  @PostMapping("/sync/full")
  public AdminDtos.SyncStatus synchronizeAll() {
    return service.synchronizeAll();
  }

  @PostMapping("/sync/incremental")
  public AdminDtos.SyncStatus synchronizeIncremental() {
    return service.synchronizeIncremental();
  }

  @PostMapping("/sync/users/{subject}")
  public AdminDtos.SyncStatus synchronizeIdentity(
      @PathVariable String subject,
      @RequestParam(defaultValue = "external") String issuer) {
    return service.synchronizeIdentity(new AuthenticatedIdentity(issuer, subject, subject));
  }

  private Pageable pageable(
      int page,
      int size,
      String requestedSort,
      String defaultProperty,
      Set<String> allowedProperties) {
    if (page < 0) throw AdminApiException.validation("page must be zero or greater");
    if (size < 1 || size > 100)
      throw AdminApiException.validation("size must be between 1 and 100");
    String[] parts = requestedSort == null ? new String[0] : requestedSort.split(",", 2);
    String property =
        parts.length > 0 && allowedProperties.contains(parts[0]) ? parts[0] : defaultProperty;
    Sort.Direction direction =
        parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    return PageRequest.of(page, size, Sort.by(direction, property));
  }
}
