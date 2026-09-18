package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.domain.UserEntitlements;
import io.github.isharafe.authorization.spi.EntitlementProvider;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Exposes the authenticated user's UI permission codes to browser applications. */
@RestController
public final class CurrentUserUiPermissionsEndpoint {
  private final SpringAuthenticationIdentityResolver identities;
  private final EntitlementProvider entitlements;

  public CurrentUserUiPermissionsEndpoint(
      SpringAuthenticationIdentityResolver identities, EntitlementProvider entitlements) {
    this.identities = identities;
    this.entitlements = entitlements;
  }

  @GetMapping("${authorization.ui-api.endpoint:/authorization/ui/permissions}")
  public ResponseEntity<UiPermissionsResponse> permissions(Authentication authentication) {
    AuthenticatedIdentity identity = identities.resolve(authentication);
    if (identity == null) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "An authenticated identity is required");
    }

    UserEntitlements loaded;
    try {
      loaded = entitlements.load(identity);
    } catch (RuntimeException failure) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Authorization infrastructure unavailable",
          failure);
    }

    List<String> permissions =
        loaded.permissions().stream()
            .filter(permission -> permission.enabled() && permission.resourceType() == ResourceType.UI)
            .map(permission -> permission.code())
            .sorted()
            .toList();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new UiPermissionsResponse(permissions, loaded.version()));
  }

  public record UiPermissionsResponse(List<String> permissions, long entitlementVersion) {
    public UiPermissionsResponse {
      permissions = List.copyOf(permissions);
    }
  }
}
