package io.github.isharafe.authorization.keycloak.event;

import io.github.isharafe.authorization.domain.IdentityChangeType;
import java.time.Instant;

public record KeycloakIdentityChangeRequest(
    String eventId, IdentityChangeType type, String userId, String occurredAt) {
  public KeycloakIdentityChangeRequest {
    require(eventId, "eventId", 255);
    require(userId, "userId", 500);
    if (type == null) throw new IllegalArgumentException("type is required");
  }

  public Instant parsedOccurredAt() {
    if (occurredAt == null || occurredAt.isBlank()) return null;
    try {
      return Instant.parse(occurredAt);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("occurredAt must be an ISO-8601 instant", exception);
    }
  }

  private static void require(String value, String name, int maximumLength) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " is required");
    if (value.length() > maximumLength)
      throw new IllegalArgumentException(name + " must not exceed " + maximumLength + " characters");
  }
}
