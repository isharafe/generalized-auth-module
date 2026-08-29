package com.example.authorization.ldap.sync;

public final class LdapSynchronizationException extends RuntimeException {
  public LdapSynchronizationException(String message) {
    super(message);
  }

  public LdapSynchronizationException(String message, Throwable cause) {
    super(message, cause);
  }
}
