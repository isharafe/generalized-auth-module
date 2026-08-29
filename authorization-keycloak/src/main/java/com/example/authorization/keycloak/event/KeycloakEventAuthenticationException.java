package com.example.authorization.keycloak.event;

public final class KeycloakEventAuthenticationException extends RuntimeException {
  public KeycloakEventAuthenticationException(String message) {
    super(message);
  }

  public KeycloakEventAuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
