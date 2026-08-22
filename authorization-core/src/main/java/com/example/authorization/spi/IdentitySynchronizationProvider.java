package com.example.authorization.spi;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.SynchronizationStatus;

public interface IdentitySynchronizationProvider {
  default boolean supported() {
    return false;
  }

  default SynchronizationStatus status() {
    return SynchronizationStatus.unsupported();
  }

  default void synchronize(AuthenticatedIdentity identity) {
    throw new IllegalStateException("Identity synchronization is not configured");
  }

  default void synchronizeAll() {
    throw new IllegalStateException("Full identity synchronization is not configured");
  }

  default void synchronizeIncremental() {
    throw new IllegalStateException("Incremental identity synchronization is not configured");
  }
}
