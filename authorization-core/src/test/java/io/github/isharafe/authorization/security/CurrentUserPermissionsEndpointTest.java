package io.github.isharafe.authorization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.Permission;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.domain.UserEntitlements;
import io.github.isharafe.authorization.engine.AuthorizationInfrastructureException;
import io.github.isharafe.authorization.spi.EntitlementProvider;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class CurrentUserPermissionsEndpointTest {
  private final SpringAuthenticationIdentityResolver identities =
      mock(SpringAuthenticationIdentityResolver.class);
  private final EntitlementProvider entitlements = mock(EntitlementProvider.class);
  private final CurrentUserPermissionsEndpoint endpoint =
      new CurrentUserPermissionsEndpoint(identities, entitlements);
  private final Authentication authentication = mock(Authentication.class);
  private final AuthenticatedIdentity identity =
      new AuthenticatedIdentity("issuer", "subject", "viewer");

  @Test
  void returnsAllEnabledPermissionCodesInStableOrder() {
    when(identities.resolve(authentication)).thenReturn(identity);
    when(entitlements.load(identity))
        .thenReturn(
            new UserEntitlements(
                identity,
                Set.of("VIEWER"),
                Set.of("EMPLOYEES"),
                Set.of(
                    permission("UI:Z_LAST", ResourceType.UI, true),
                    permission("URL:EMPLOYEES", ResourceType.URL, true),
                    permission("UI:A_FIRST", ResourceType.UI, true),
                    permission("UI:DISABLED", ResourceType.UI, false)),
                7,
                Instant.parse("2026-09-18T00:00:00Z")));

    var response = endpoint.permissions(authentication);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().permissions())
        .containsExactly("UI:A_FIRST", "UI:Z_LAST", "URL:EMPLOYEES");
    assertThat(response.getBody().entitlementVersion()).isEqualTo(7);
    assertThat(response.getHeaders().getCacheControl())
        .isEqualTo(CacheControl.noStore().getHeaderValue());
  }

  @Test
  void rejectsRequestsWithoutAResolvedIdentity() {
    when(identities.resolve(authentication)).thenReturn(null);

    assertThatThrownBy(() -> endpoint.permissions(authentication))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode().value()).isEqualTo(401));
  }

  @Test
  void mapsEntitlementFailuresToServiceUnavailable() {
    when(identities.resolve(authentication)).thenReturn(identity);
    when(entitlements.load(identity))
        .thenThrow(
            new AuthorizationInfrastructureException(
                "database unavailable", new IllegalStateException("offline")));

    assertThatThrownBy(() -> endpoint.permissions(authentication))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode().value()).isEqualTo(503));
  }

  private Permission permission(String code, ResourceType type, boolean enabled) {
    return new Permission(code, code, null, type, code.toLowerCase(), enabled);
  }
}
