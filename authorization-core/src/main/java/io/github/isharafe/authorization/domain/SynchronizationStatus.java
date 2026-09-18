package io.github.isharafe.authorization.domain;

import java.time.Instant;

public record SynchronizationStatus(
    boolean supported, String provider, String status, Instant updatedAt, String details) {
  public static SynchronizationStatus unsupported() {
    return new SynchronizationStatus(false, null, "UNSUPPORTED", null, null);
  }
}
