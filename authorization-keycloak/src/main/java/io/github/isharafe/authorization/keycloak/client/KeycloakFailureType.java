package io.github.isharafe.authorization.keycloak.client;

public enum KeycloakFailureType {
  AUTHENTICATION,
  NOT_FOUND,
  RATE_LIMITED,
  REMOTE_SERVER,
  TRANSPORT,
  INVALID_RESPONSE
}
