package io.github.isharafe.authorization.keycloak.sync;

public class KeycloakSynchronizationException extends RuntimeException {
  public KeycloakSynchronizationException(String message) {
    super(message);
  }

  public KeycloakSynchronizationException(String message, Throwable cause) {
    super(message, cause);
  }
}
