package io.github.isharafe.authorization.admin.api;

import static io.github.isharafe.authorization.admin.api.AdminPageableFactory.create;

import io.github.isharafe.authorization.admin.dto.AdminDtos;
import io.github.isharafe.authorization.admin.service.AuthorizationUserAdminService;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
public class AuthorizationUserController {
  private static final Set<String> USER_SORTS =
      Set.of("id", "issuer", "subject", "username", "email", "enabled", "version");
  private final AuthorizationUserAdminService service;

  @GetMapping("/current-user")
  public AdminDtos.CurrentUser currentUser(Authentication authentication) {
    return service.currentUser(authentication);
  }

  @GetMapping("/users")
  public AdminDtos.Page<AdminDtos.User> users(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "id,asc") String sort,
      @RequestParam(defaultValue = "") String search) {
    return service.users(search, create(page, size, sort, "id", USER_SORTS));
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
}
