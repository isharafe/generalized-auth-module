package com.example.authorization.domain;

import java.util.Objects;

public record AuthenticatedIdentity(String issuer, String subject, String username) {
  public AuthenticatedIdentity {
    Objects.requireNonNull(issuer, "issuer");
    Objects.requireNonNull(subject, "subject");
  }

  public String cacheKey() {
    return issuer + "\u0000" + subject;
  }
}
