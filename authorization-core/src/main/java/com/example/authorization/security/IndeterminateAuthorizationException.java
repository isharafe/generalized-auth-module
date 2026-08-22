package com.example.authorization.security;

import org.springframework.security.access.AccessDeniedException;

public final class IndeterminateAuthorizationException extends AccessDeniedException {
  public IndeterminateAuthorizationException() {
    super("Authorization infrastructure unavailable");
  }
}
