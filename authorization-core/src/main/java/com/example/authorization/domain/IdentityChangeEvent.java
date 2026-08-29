package com.example.authorization.domain;

import java.time.Instant;

public record IdentityChangeEvent(
    String eventId,
    String sourceSystem,
    AuthenticatedIdentity identity,
    IdentityChangeType type,
    Instant occurredAt) {
  public IdentityChangeEvent {
    require(eventId, "eventId", 255);
    require(sourceSystem, "sourceSystem", 50);
    if (identity == null) throw new IllegalArgumentException("identity is required");
    require(identity.issuer(), "identity.issuer", 500);
    require(identity.subject(), "identity.subject", 500);
    if (type == null) throw new IllegalArgumentException("type is required");
  }

  private static void require(String value, String name, int maximumLength) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " is required");
    if (value.length() > maximumLength)
      throw new IllegalArgumentException(name + " must not exceed " + maximumLength + " characters");
  }
}
