package com.example.authorization.persistence.service;

public class IdentityChangeProcessingException extends RuntimeException {
  public IdentityChangeProcessingException(String message) {
    super(message);
  }

  public IdentityChangeProcessingException(String message, Throwable cause) {
    super(message, cause);
  }
}
