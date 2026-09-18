package io.github.isharafe.authorization.persistence.service;

public final class IdentityChangeEventConflictException
    extends IdentityChangeProcessingException {
  public IdentityChangeEventConflictException(String message) {
    super(message);
  }
}
