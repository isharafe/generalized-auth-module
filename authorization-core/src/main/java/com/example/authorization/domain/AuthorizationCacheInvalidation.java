package com.example.authorization.domain;

import java.time.Instant;

public record AuthorizationCacheInvalidation(
    String eventId,
    String origin,
    Scope scope,
    String issuer,
    String subject,
    Instant createdAt) {
  public AuthorizationCacheInvalidation {
    require(eventId, "eventId", 100);
    require(origin, "origin", 100);
    if (scope == null) throw new IllegalArgumentException("scope is required");
    if (scope == Scope.IDENTITY) {
      require(issuer, "issuer", 500);
      require(subject, "subject", 500);
    }
    if (createdAt == null) throw new IllegalArgumentException("createdAt is required");
  }

  private static void require(String value, String name, int maximumLength) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " is required");
    if (value.length() > maximumLength)
      throw new IllegalArgumentException(name + " must not exceed " + maximumLength + " characters");
  }

  public enum Scope {
    IDENTITY,
    RESOURCE_RULES,
    ALL
  }
}
