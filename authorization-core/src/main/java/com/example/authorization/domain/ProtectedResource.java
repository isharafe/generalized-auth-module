package com.example.authorization.domain;

import java.util.Objects;

public record ProtectedResource(ResourceType resourceType, String pattern) {
  public ProtectedResource {
    Objects.requireNonNull(resourceType, "resourceType");
    pattern = Objects.requireNonNull(pattern, "pattern");
    if (pattern.isBlank()) throw new IllegalArgumentException("pattern must not be blank");
  }
}
