package com.example.authorization.keycloak.client;

public class KeycloakClientException extends RuntimeException {
  private final KeycloakFailureType failureType;
  private final Integer status;

  public KeycloakClientException(
      KeycloakFailureType failureType, Integer status, String message, Throwable cause) {
    super(message, cause);
    this.failureType = failureType;
    this.status = status;
  }

  public KeycloakClientException(
      KeycloakFailureType failureType, Integer status, String message) {
    this(failureType, status, message, null);
  }

  public KeycloakFailureType failureType() {
    return failureType;
  }

  public Integer status() {
    return status;
  }
}
