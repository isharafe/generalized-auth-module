package io.github.isharafe.authorization.ldap.client;

public final class LdapClientException extends RuntimeException {
  private final LdapFailureType failureType;

  public LdapClientException(LdapFailureType failureType, String message, Throwable cause) {
    super(message, cause);
    this.failureType = failureType;
  }

  public LdapClientException(LdapFailureType failureType, String message) {
    super(message);
    this.failureType = failureType;
  }

  public LdapFailureType failureType() {
    return failureType;
  }
}
