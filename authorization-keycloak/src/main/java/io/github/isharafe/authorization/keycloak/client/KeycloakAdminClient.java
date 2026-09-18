package io.github.isharafe.authorization.keycloak.client;

import java.util.List;
import java.util.Optional;

public interface KeycloakAdminClient {
  List<KeycloakUser> users();

  Optional<KeycloakUser> user(String id);

  List<KeycloakGroup> groups(String userId);

  List<KeycloakRole> realmRoles(String userId);
}
