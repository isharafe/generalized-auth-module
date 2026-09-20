package io.github.isharafe.authorization.admin.api;

import static io.github.isharafe.authorization.admin.api.AdminPageableFactory.create;

import io.github.isharafe.authorization.admin.dto.AdminDtos;
import io.github.isharafe.authorization.admin.service.AuthorizationCatalogAdminService;
import io.github.isharafe.authorization.domain.ResourceType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
public class AuthorizationCatalogController {
  private static final Set<String> SORTS = Set.of("code", "name", "enabled", "version");
  private final AuthorizationCatalogAdminService service;

  @GetMapping("/roles")
  public AdminDtos.Page<AdminDtos.Role> roles(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "code,asc") String sort,
      @RequestParam(defaultValue = "") String search) {
    return service.roles(search, create(page, size, sort, "code", SORTS));
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
    return service.permissionGroups(search, create(page, size, sort, "code", SORTS));
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
    return service.permissions(search, create(page, size, sort, "code", SORTS));
  }

  @PostMapping("/permissions")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.Permission createPermission(@Valid @RequestBody AdminDtos.Permission request) {
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
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) ResourceType resourceType) {
    return service.resourceRules(
        search, resourceType, create(page, size, sort, "code", SORTS));
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
}
